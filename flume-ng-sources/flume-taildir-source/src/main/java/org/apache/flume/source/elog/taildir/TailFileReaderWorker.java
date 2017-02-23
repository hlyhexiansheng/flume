package org.apache.flume.source.elog.taildir;

import org.apache.flume.Event;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.event.EventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by noodles on 2017/2/18 23:39.
 */

public class TailFileReaderWorker extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(TailFileReaderWorker.class);

    private volatile boolean running = true;
    private volatile boolean isFileRolling = false;

    private Map<String, TailFile> fileMap = new ConcurrentHashMap<>();

    private BlockingQueue<String> readEventQueue = new LinkedBlockingQueue<>();

    private TailFilePositionManager tailFilePositionManager;

    private ChannelProcessor channelProcessor;

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();


    public TailFileReaderWorker(TailFilePositionManager tailFilePositionManager, ChannelProcessor channelProcessor) {
        this.tailFilePositionManager = tailFilePositionManager;
        this.channelProcessor = channelProcessor;
    }

    @Override
    public void run() {
        this.scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {

                    fireAllFileEvent();

                    tailFilePositionManager.syncDisk();

                } catch (IOException e) {
                    logger.error("schedule catch exception.", e);
                }
            }
        }, 5, 4, TimeUnit.SECONDS);

        while (running) {
            try {
                final String inode = readEventQueue.take();
                final TailFile tailFile = fileMap.get(inode);

                if (tailFile == null) {
                    logger.warn("tailFile is null,inode=" + inode + "mayby cause by file rolling.");
                    continue;
                }
                doRead(tailFile);

                this.tailFilePositionManager.updatePosition(tailFile.getInode(), tailFile.getOffset());


            } catch (Exception e) {
                logger.error("read tail file catch exception", e);
            }
        }

        try {
            logger.info("TailFileReaderWorker worker stop");
            this.tailFilePositionManager.syncDisk();
            this.closeAllFile();
            this.scheduledExecutorService.shutdown();
        } catch (IOException e) {
            logger.error("closeAllFile catch exception", e);
        }

    }

    private void doRead(TailFile tailFile) throws IOException {

        while (true) {
            if (isFileRolling) {
                return;
            }
            final String line = tailFile.nextLine();
            if (line == null) {
                break;
            }
            final Event event = EventBuilder.withBody(line.getBytes());
            this.channelProcessor.processEvent(event);
        }
    }

    public void addNewFile(String inode, TailFile tailFile) {
        if (!fileMap.containsKey(inode)) {
            fileMap.put(inode, tailFile);
        }
    }

    private void fireAllFileEvent() {
        final Set<String> nodes = this.fileMap.keySet();
        for (String inode : nodes) {
            readEventQueue.offer(inode);
        }
    }

    public void closeAllFile() throws IOException {

        final Collection<TailFile> values = fileMap.values();
        for (TailFile file : values) {
            file.close();
        }
        fileMap.clear();
    }

    public List<TailFilePositionInfo> getPositionInfo() {
        List<TailFilePositionInfo> infoList = new ArrayList<>();
        final Collection<TailFile> values = fileMap.values();
        for (TailFile file : values) {
            TailFilePositionInfo info = new TailFilePositionInfo(file.getInode(), file.getFilename(), file.getOffset());
            infoList.add(info);
        }
        return infoList;
    }


    public void setRunning(boolean running) {
        this.running = running;
    }

    public void setFileRolling(boolean fileRolling) {
        isFileRolling = fileRolling;
    }

}

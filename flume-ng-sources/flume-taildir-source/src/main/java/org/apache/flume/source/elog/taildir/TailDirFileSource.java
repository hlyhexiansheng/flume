package org.apache.flume.source.elog.taildir;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.flume.Context;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by noodles on 2017/2/18 23:00.
 */
public class TailDirFileSource extends AbstractSource implements Configurable, EventDrivenSource {

    private static final Logger logger = LoggerFactory.getLogger(TailDirFileSource.class);

    private static final String FILE_SPLIT_KEY = ";";

    private volatile boolean running = false;

    private String positionFile;

    private WatchService watchService;

    private Map<WatchKey, Path> keys = Maps.newHashMap();

    private TailFileReaderWorker tailFileReaderWorker;

    private TailFilePositionManager tailFilePositionManager;

    //监控目录列表
    private List<String> watchDirs = new ArrayList<>();
    //文件名过滤列表
    private List<String> fileFilterKeys = new ArrayList<>();

    @Override
    public void configure(Context context) {
        final String watchDirs = context.getString("watchDirs");
        final String fileFilterStr = context.getString("fileFilterKeys");
        this.positionFile = context.getString("positionFile");

        logger.info("TailDirFileSource configure: watchDirs=[" + watchDirs + "],positionFile=[" + positionFile + "],fileFilterKeys=[" + fileFilterStr + "]");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(watchDirs), "watchDirs is NULL..");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(this.positionFile), "positionFile is NULL");

        Path path = Paths.get(this.positionFile);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            logger.error("create position file directory fail");
        }

        final String[] watchSplit = watchDirs.split(FILE_SPLIT_KEY);
        for (String dir : watchSplit) {
            if (!Strings.isNullOrEmpty(dir)) {
                this.watchDirs.add(dir);
            }
        }

        if (!Strings.isNullOrEmpty(fileFilterStr)) {
            final String[] fileFilterSplit = fileFilterStr.split(FILE_SPLIT_KEY);
            for (String filter : fileFilterSplit) {
                if (!Strings.isNullOrEmpty(filter)) {
                    this.fileFilterKeys.add(filter);
                }
            }
        }
    }


    @Override
    public synchronized void start() {
        logger.info("ElogTailDirSource started...");
        super.start();

        running = true;

        try {
            this.tailFilePositionManager = new TailFilePositionManager(positionFile, watchDirs, this.fileFilterKeys);
            this.tailFileReaderWorker = new TailFileReaderWorker(tailFilePositionManager, getChannelProcessor());
            this.watchService = FileSystems.getDefault().newWatchService();

            this.resetTailFiles();

            for (String dir : watchDirs) {
                if (TailFileHelper.isExist(dir) && TailFileHelper.isDirectory(dir)) {
                    final Path path = Paths.get(dir);
                    final WatchKey watchKey = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                    keys.put(watchKey, path);
                } else {
                    logger.error("[{}] is not exist or is not a directory.", dir);
                }
            }

            this.tailFileReaderWorker.start();

            while (running) {

                final WatchKey key = watchService.take();
                final Path filePath = keys.get(key);

                List<WatchEvent<?>> watchEvents = key.pollEvents();

                process(watchEvents, filePath);

                key.reset();
            }
        } catch (Exception e) {
            logger.error("TailDirFileSource catch Exception..", e);
        }

        logger.info("TailDirFileSource stop...");
    }

    private void resetTailFiles() throws IOException {
        //1.更新offset
        final List<TailFilePositionInfo> curPostionInfos = this.tailFileReaderWorker.getPositionInfo();
        for (TailFilePositionInfo info : curPostionInfos) {
            this.tailFilePositionManager.updatePosition(info.getInode(), info.getOffset());
        }
        this.tailFilePositionManager.syncDisk();

        //2.关闭旧的文件
        this.tailFileReaderWorker.closeAllFile();

        //3.重新加入文件.
        final List<TailFilePositionInfo> positionInfos = this.tailFilePositionManager.getPositionInfos();
        for (TailFilePositionInfo info : positionInfos) {
            String inode = info.getInode();
            TailFile tailFile = new TailFile(inode, info.getFilename(), info.getOffset());
            this.tailFileReaderWorker.addNewFile(inode, tailFile);
        }
    }


    private void process(List<WatchEvent<?>> watchEvents, final Path path) throws IOException {
        for (WatchEvent<?> event : watchEvents) {
            if (event.context() != null) {

                String fileName = path.toAbsolutePath().toString() + "/" + event.context().toString();
                if (TailFileHelper.isDirectory(fileName)) {
                    continue;
                }
                // 文件创建
                if (event.kind().name().equals(StandardWatchEventKinds.ENTRY_CREATE.name())) {
                    processCreate(fileName);
                }
            }
        }
    }

    private void processCreate(String fileName) throws IOException {
        logger.info("create file-[{}]", fileName);

        //1.加锁，让work暂时不要再读了 （使用volatile变量当作轻量级锁）
        this.tailFileReaderWorker.setFileRolling(true);

        //2.触发create事件，主要是刷新文件的位置信息
        this.tailFilePositionManager.notifyFileCreate();

        //3.重置work监控的文件
        this.resetTailFiles();

        //4.释放标志位
        this.tailFileReaderWorker.setFileRolling(false);

    }

    @Override
    public synchronized void stop() {
        this.running = false;
        this.tailFileReaderWorker.setRunning(false);
        super.stop();
    }

}

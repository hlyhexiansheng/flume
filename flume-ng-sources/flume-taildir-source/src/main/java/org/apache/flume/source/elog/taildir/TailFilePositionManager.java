package org.apache.flume.source.elog.taildir;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by noodles on 2017/2/19 12:19.
 */
public class TailFilePositionManager {

    private RandomAccessFile randomAccessFile;

    private List<String> watchDirs;

    private List<TailFilePositionInfo> positionInfos;

    private List<String> fileFilterKeys;

    public TailFilePositionManager(String positionFile, List<String> watchDirs, List<String> fileFilterKeys) throws IOException {
        this.watchDirs = watchDirs;
        this.fileFilterKeys = fileFilterKeys;
        this.randomAccessFile = new RandomAccessFile(new File(positionFile), "rw");

        reloadPositionInfo();

        syncDisk();
    }


    /**
     * 同步数据到磁盘
     *
     * @throws IOException
     */
    public void syncDisk() throws IOException {
        final String content = new Gson().toJson(positionInfos);
        randomAccessFile.setLength(0);
        randomAccessFile.write(content.getBytes(Charset.forName("utf-8")));
    }

    public List<TailFilePositionInfo> getPositionInfos() {
        return this.positionInfos;
    }

    public void notifyFileCreate() throws IOException {

        syncDisk();

        reloadPositionInfo();

        syncDisk();

    }


    public void updatePosition(String inode, long offset) throws IOException {
        for (TailFilePositionInfo info : positionInfos) {
            if (info.getInode().equals(inode)) {
                info.setOffset(offset);
                break;
            }
        }
    }

    /**
     * 加载文件位置信息
     *
     * @throws IOException
     */
    private void reloadPositionInfo() throws IOException {

        //1.读出文件
        int length = (int) randomAccessFile.length();
        byte[] bytes = new byte[length];
        randomAccessFile.seek(0);
        randomAccessFile.read(bytes);

        //2.转换成JavaBean
        final String jsonContent = new String(bytes, Charset.forName("utf-8"));
        if (jsonContent.equals("")) {
            positionInfos = new ArrayList<>();
        } else {
            Type listType = new TypeToken<ArrayList<TailFilePositionInfo>>() {
            }.getType();
            positionInfos = new Gson().fromJson(jsonContent, listType);
        }

        //3.获得所有文件对应的File Node
        final List<String> allInode = getAllNode(this.watchDirs,this.fileFilterKeys);


        if (allInode == null || allInode.size() == 0) {
            positionInfos.clear();
        } else {
            for (String inode : allInode) {
                if (!containsInode(inode, positionInfos)) {
                    TailFilePositionInfo info = new TailFilePositionInfo(inode, getFileName(inode, this.watchDirs), 0);
                    positionInfos.add(info);
                } else {
                    for (TailFilePositionInfo info : positionInfos) {
                        info.setFilename(getFileName(info.getInode(), this.watchDirs)); //文件发生滚动之后，原来的inode，对应的文件名称变了，这里重新设置文件名
                    }
                }
            }
            removePositionInfoIfFileNodeNotExist(positionInfos, allInode);//最后去掉文件被删了，但是positionInfo还在的
        }
    }


    private static void removePositionInfoIfFileNodeNotExist(List<TailFilePositionInfo> infos, List<String> allNodes) {
        if (infos == null || infos.size() == 0) {
            return;
        }
        if (allNodes == null || allNodes.size() == 0) {
            infos.clear();
            return;
        }

        List<String> toDeleteINode = new ArrayList<>();
        for (TailFilePositionInfo info : infos) {
            String inode = info.getInode();
            if (!allNodes.contains(inode)) {
                toDeleteINode.add(inode);
            }
        }

        for (String delNode : toDeleteINode) {
            removePostionInfoByINode(infos, delNode);
        }
    }


    private static void removePostionInfoByINode(List<TailFilePositionInfo> infos, String node) {
        if (infos == null || infos.size() == 0 || node == null || node.equals("")) {
            return;
        }
        for (int i = 0; i < infos.size(); i++) {
            if (infos.get(i).getInode().equals(node)) {
                infos.remove(i);
                break;
            }
        }

    }

    private static boolean containsInode(String inode, List<TailFilePositionInfo> infos) {
        for (TailFilePositionInfo info : infos) {
            if (info.getInode().equals(inode)) {
                return true;
            }
        }
        return false;
    }

    public String getFileName(String inode, List<String> directorys) {
        for (String dir : directorys) {
            final String fileName = TailFileHelper.getFileName(dir, inode);
            if (fileName != null) {
                return fileName;
            }
        }
        return null;
    }

    public List<String> getAllNode(List<String> watchDirs, List<String> filterList) {
        final List<String> allInode = new ArrayList<>();
        for (String dir : watchDirs) {
            final List<String> nodes = TailFileHelper.getAllInode(dir, filterList);
            if (nodes != null) {
                allInode.addAll(nodes);
            }
        }
        return allInode;
    }

}

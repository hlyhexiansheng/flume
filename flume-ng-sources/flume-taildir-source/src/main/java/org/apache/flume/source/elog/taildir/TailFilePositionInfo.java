package org.apache.flume.source.elog.taildir;

/**
 * Created by noodles on 2017/2/19 01:18.
 */
public class TailFilePositionInfo {


    private String inode;
    private String filename;
    private long offset;

    public TailFilePositionInfo() {
    }

    public TailFilePositionInfo(String inode, String filename, long offset) {
        this.inode = inode;
        this.filename = filename;
        this.offset = offset;
    }

    public String getInode() {
        return inode;
    }

    public void setInode(String inode) {
        this.inode = inode;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }
}

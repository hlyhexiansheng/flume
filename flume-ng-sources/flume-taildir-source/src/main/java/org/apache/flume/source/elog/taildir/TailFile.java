package org.apache.flume.source.elog.taildir;


import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by noodles on 2017/2/18 23:29.
 */

public class TailFile {
    private String inode;
    private String filename;
    private long offset;
    private RandomAccessFile randomAccessFile;

    public TailFile(String inode, String filename, long offset) {
        try {
            this.inode = inode;
            this.filename = filename;
            this.offset = offset;
            this.randomAccessFile = new RandomAccessFile(filename, "r");
            this.randomAccessFile.seek(offset);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        if (this.randomAccessFile != null) {
            this.randomAccessFile.close();
        }
    }

    public String nextLine() throws IOException {
        final String s = randomAccessFile.readLine();
        offset = randomAccessFile.getFilePointer();
        return s;
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

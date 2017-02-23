package org.apache.flume.source.elog.taildir;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by noodles on 2017/2/18 17:32.
 */
public class TailFileHelper {

    public static String getFileName(String directory, String inode) {
        if (inode == null || !isExist(directory) || !isDirectory(directory)) {
            return null;
        }

        final File[] allFiles = new File(directory).listFiles();
        if (allFiles == null) {
            return null;
        }

        for (File file : allFiles) {
            if (inode.equals(getInode(file.getAbsolutePath()))) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * 获取目录下的所有文件的inode，不包含目录的
     *
     * @param directory
     * @param filterList 文件名必须保护的关键字
     * @return inodeList
     */
    public static List<String> getAllInode(String directory, final List<String> filterList) {

        if (!isExist(directory) || !isDirectory(directory)) {
            return null;
        }

        final File dir = new File(directory);

        final File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (isDirectory(dir.getAbsolutePath() + "/" + name)) {
                    return false;
                }
                return containsFileName(name, filterList);
            }
        });

        if (files == null || files.length == 0) {
            return null;
        }

        List<String> resultList = new ArrayList<>();
        for (File file : files) {
            final String inode = getInode(file.getAbsolutePath());
            resultList.add(inode);
        }

        return resultList;
    }

    public static boolean containsFileName(String fileName, List<String> filterList) {
        if (filterList == null || filterList.size() == 0) {
            return true;
        }
        for (String filter : filterList) {
            if (fileName.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isExist(String filename) {
        return new File(filename).exists();
    }

    public static boolean isDirectory(String filename) {
        return new File(filename).isDirectory();
    }

    public static String getInode(String fileName) {
        String inode;
        try {
            Path path = Paths.get(fileName);
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);

            Object fileKey = attr.fileKey();
            String s = fileKey.toString();
            inode = s.substring(s.indexOf("ino=") + 4, s.indexOf(")"));
        } catch (Exception e) {
            return null;
        }

        return inode;
    }
}

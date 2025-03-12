package com.photons.carrycloud.localfile.objects;


import com.photons.carrycloud.utils.FileUtils;

public class FileInfo {
    public enum AccessType {Open, Protected}

    public enum FileType {Folder, Video, Audio, Image, Text, Unknown, File, Zip, Parent}

    private String fileName;
    private long fileCount;//如果是文件夹则表示子目录项数,如果不是文件夹则表示文件大小，-1不显示
    private String lastUpdateTime;
    private String FilePath;
    private FileType fileType;
    private AccessType accessType;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileCount() {
        if (fileType == FileType.Parent)
            return "";
        else if (fileCount == -1 && fileType == FileType.Folder)
            return "受保护的文件夹";
        else if (fileType == FileType.Folder)
            return "共" + fileCount + "项";
        else {
            return FileUtils.INSTANCE.getFileSize(fileCount);
        }
    }

    public void setFileCount(long fileCount) {
        this.fileCount = fileCount;
    }

    public String getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getFilePath() {
        return FilePath;
    }

    public void setFilePath(String filePath) {
        FilePath = filePath;
    }

    public FileType getFileType() {
        return fileType;
    }

    public boolean isDirectory() {
        return fileType == FileType.Folder;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public static AccessType judgeAccess(String path) {
        boolean isProtectedDir = false;
        if (path.contains("Android/data")) isProtectedDir = true;
        if (path.contains("Android/obb")) isProtectedDir = true;
        return isProtectedDir ? AccessType.Protected : AccessType.Open;
    }

    public AccessType getAccessType() {
        if (accessType == null) {
            accessType = judgeAccess(FilePath);
        }
        return accessType;
    }

    public void setAccessType(AccessType accessType) {
        this.accessType = accessType;
    }

    public boolean FileFilter(FileType[] types) {
        for (FileType type : types) {
            if (this.fileType == type) return true;
            if (type == FileType.File && this.fileType != FileType.Folder) return true;
        }
        return false;
    }
}

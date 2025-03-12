package com.photons.carrycloud.ui.fileselector;

import android.os.Environment;

import com.photons.carrycloud.localfile.objects.FileInfo;


public class FileSelectOptions {
    public static final String BasicPath = Environment.getExternalStorageDirectory().getAbsolutePath();

    public static String SELECTED_PATH = "file_path_list";
    public static int BACK_WITHOUT_SELECT = 510;
    public static int BACK_WITH_SELECTIONS = 511;
    public static int FILE_LIST_REQUEST_CODE = 512;

    private String RootPath;
    private int MaxSelectNum;
    private String tips;
    private FileInfo.FileType[] selectableFileTypes;
    private String[] fileTypeFilter;
    private boolean useFilter;
    private boolean isDirectory = true;

    public String getRootPath() {
        return RootPath;
    }

    public void setRootPath(String rootPath) {
        RootPath = rootPath;
    }

    public int getMaxSelectNum() {
        return MaxSelectNum;
    }

    public void setMaxSelectNum(int maxSelectNum) {
        MaxSelectNum = maxSelectNum;
    }

    public String getTips() {
        return tips;
    }

    public void setTips(String tips) {
        this.tips = tips;
    }

    public void setSelectableFileTypes(FileInfo.FileType... selectableFileTypes) {
        this.selectableFileTypes = selectableFileTypes;
    }

    public boolean isSelectDirectory() {
        return isDirectory;
    }

    public FileInfo.FileType[] getSelectableFileTypes() {
        return selectableFileTypes;
    }

    public String[] getFileTypeFilter() {
        return fileTypeFilter;
    }

    public void setFileTypeFilter(String[] fileTypeFilter) {
        this.isDirectory = false;
        this.fileTypeFilter = fileTypeFilter;
    }

    public boolean isUseFilter() {
        return useFilter;
    }

    public void setUseFilter(boolean useFilter) {
        this.useFilter = useFilter;
    }

    public static FileSelectOptions getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public static FileSelectOptions getInitInstance() {
        FileSelectOptions op = InstanceHolder.INSTANCE;
        op.setRootPath(BasicPath);
        op.setMaxSelectNum(-1);
        op.setUseFilter(true);
        return op;
    }

    private static final class InstanceHolder {
        private static final FileSelectOptions INSTANCE = new FileSelectOptions();
    }
}

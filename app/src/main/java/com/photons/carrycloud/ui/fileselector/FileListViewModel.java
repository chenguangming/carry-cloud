package com.photons.carrycloud.ui.fileselector;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.photons.carrycloud.localfile.objects.FileInfo;
import com.photons.carrycloud.utils.FileUtils;

import java.util.ArrayList;

public class FileListViewModel extends ViewModel {
    private MutableLiveData<ArrayList<FileInfo>> fileList;

    public MutableLiveData<ArrayList<FileInfo>> getFileList() {
        if (fileList == null) {
            ArrayList<FileInfo> initList = new ArrayList<>();
            fileList = new MutableLiveData<>();
            fileList.setValue(initList);
        }
        return fileList;
    }

    public void addToResult(FileInfo fileInfo) {
        ArrayList<FileInfo> new_list = fileList.getValue();
        if (new_list != null) new_list.add(fileInfo);
        fileList.setValue(new_list);
    }

    public void addToResult(ArrayList<FileInfo> fileInfo) {
        ArrayList<FileInfo> new_list = fileList.getValue();
        if (new_list != null) new_list.addAll(fileInfo);
        fileList.setValue(new_list);
    }

    public void clear() {
        ArrayList<FileInfo> emptyList = new ArrayList<>();
        fileList.setValue(emptyList);
    }

    public void sortByName() {
        ArrayList<FileInfo> new_list = fileList.getValue();
        if (new_list == null || new_list.size() <= 1) {
            return;
        }

        if (new_list.get(0).getFileType() == FileInfo.FileType.Parent) {
            FileInfo header = new_list.remove(0);
            FileUtils.INSTANCE.sortFilesByName(new_list);
            new_list.add(0, header);
        } else {
            FileUtils.INSTANCE.sortFilesByName(new_list);
        }
    }
}

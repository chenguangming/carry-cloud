package com.photons.carrycloud.ui.fileselector.adapter;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.photons.carrycloud.R;
import com.photons.carrycloud.localfile.objects.FileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class FileListAdapter extends BaseAdapter {
    private ArrayList<FileInfo> mFileList;
    private HashMap<Integer, Boolean> SelectionMap = new HashMap<>();
    private final Context mContext;
    private final LayoutInflater inflater;
    private boolean isSelect = false;

    public FileListAdapter(ArrayList<FileInfo> fileList, Context context) {
        mFileList = fileList;
        mContext = context;
        inflater = LayoutInflater.from(context);
        clearSelections();
    }

    public void clearSelections() {
        for (int i = 0; i < mFileList.size(); i++) {
            SelectionMap.put(i, false);
        }
    }

    public void updateFileList(ArrayList<FileInfo> fileList) {
        this.mFileList = fileList;
        notifyDataSetChanged();
    }

    public void setSelect(boolean select) {
        isSelect = select;
        notifyDataSetChanged();
    }

    public void notifyFileSelected(int position, boolean isSelected) {
        SelectionMap.put(position, isSelected);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mFileList.size();
    }

    @Override
    public Object getItem(int position) {
        return mFileList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder = null;
        if (convertView == null) {
            viewHolder = new ViewHolder();
            convertView = inflater.inflate(R.layout.file_list_item, null);
            viewHolder.ivFileIcon = convertView.findViewById(R.id.FileIcon);
            viewHolder.tvFileCount = convertView.findViewById(R.id.FileCount);
            viewHolder.tvFileName = convertView.findViewById(R.id.FileName);
            viewHolder.tvFileDate = convertView.findViewById(R.id.FileDate);
            viewHolder.ckSelector = convertView.findViewById(R.id.select_box);
            viewHolder.llInfo = convertView.findViewById(R.id.FileInfo);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        viewHolder.tvFileName.setText(mFileList.get(position).getFileName());
        viewHolder.tvFileCount.setText(mFileList.get(position).getFileCount());
        viewHolder.tvFileDate.setText(mFileList.get(position).getLastUpdateTime());

        setIcon(position, viewHolder);
        if (mFileList.get(position).getFileType() == FileInfo.FileType.Parent) {
            ViewShow(viewHolder.llInfo, View.INVISIBLE, 0);
            viewHolder.tvFileName.setGravity(Gravity.CENTER_VERTICAL);
        } else {
            ViewShow(viewHolder.llInfo, View.VISIBLE, 25);
        }

        if (isSelect && mFileList.get(position).getFileType() != FileInfo.FileType.Parent) {
            viewHolder.ckSelector.setVisibility(View.VISIBLE);
        } else {
            viewHolder.ckSelector.setVisibility(View.INVISIBLE);
        }

        if (SelectionMap != null && isSelect && viewHolder.ckSelector != null) {
            viewHolder.ckSelector.setChecked(!Objects.isNull(SelectionMap.get(position)) && (boolean) SelectionMap.get(position));
        }

        return convertView;
    }

    private void setIcon(int position, ViewHolder viewHolder) {
        FileInfo currentFile = mFileList.get(position);
        switch (currentFile.getFileType()) {
            case Folder: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_folder);
            }
            break;
            case Parent: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.fs_back_to_parent);
            }
            break;
            case Image: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_image);
            }
            break;
            case Audio: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_audio);
            }
            break;
            case Video: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_video);
            }
            break;
            case Text: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_text);
            }
            break;
            case Zip: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_zip);
            }
            break;
            case Unknown: {
                viewHolder.ivFileIcon.setImageResource(R.mipmap.file_unknown);
            }
            break;
            default:
                break;
        }
    }

    private void ViewShow(ViewGroup view, int visible, int dp) {
        view.setVisibility(visible);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = (int) (dp * mContext.getResources().getDisplayMetrics().density);
        view.setLayoutParams(params);
    }

    public static class ViewHolder {
        public LinearLayout llInfo;
        public ImageView ivFileIcon;
        public TextView tvFileName, tvFileCount, tvFileDate;
        public CheckBox ckSelector;
    }
}



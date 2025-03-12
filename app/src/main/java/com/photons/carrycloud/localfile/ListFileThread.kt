package com.photons.carrycloud.localfile

import android.os.Handler
import android.os.Message
import androidx.documentfile.provider.DocumentFile
import com.photons.carrycloud.localfile.objects.FileInfo
import com.photons.carrycloud.ui.fileselector.FileSelectOptions
import com.photons.carrycloud.utils.FileUtils.changeToPath
import com.photons.carrycloud.utils.FileUtils.fileFilter
import com.photons.carrycloud.utils.FileUtils.getSubFolderNum
import com.photons.carrycloud.utils.FileUtils.getSubfolderNum
import com.photons.carrycloud.utils.FileUtils.isAudioFileType
import com.photons.carrycloud.utils.FileUtils.isImageFileType
import com.photons.carrycloud.utils.FileUtils.isTextFileType
import com.photons.carrycloud.utils.FileUtils.isVideoFileType
import com.photons.carrycloud.utils.FileUtils.isZipFileType
import com.photons.carrycloud.utils.TimeUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch

class ListFileThread : Thread {
    private var handler: Handler? = null
    private var countDownLatch: CountDownLatch? = null
    private var fileInfoList = ArrayList<FileInfo>()
    private lateinit var fileList: Array<File>
    private lateinit var protectedFileList: Array<DocumentFile>
    private var accessType: FileInfo.AccessType

    fun setHandler(handler: Handler?) {
        this.handler = handler
    }

    fun setCountDownLatch(countDownLatch: CountDownLatch?) {
        this.countDownLatch = countDownLatch
    }

    private fun callback(event: Int, files: ArrayList<FileInfo>?) {
        handler?.let {
            it.obtainMessage().let { msg ->
                msg.what = event
                msg.obj = files
                it.sendMessage(msg)
            }
        }
    }

    constructor(files: Array<File>) {
        this.fileList = files
        this.accessType = FileInfo.AccessType.Open
    }

    constructor(protectedFiles: Array<DocumentFile>) {
        this.protectedFileList = protectedFiles
        this.accessType = FileInfo.AccessType.Protected
    }

    //隐藏文件不显示
    private val openFileList: Unit
        get() {
            fileInfoList.clear()
            for (f in fileList) {
                if (f.name.indexOf(".") != 0) {
                    //隐藏文件不显示
                    if (f.isDirectory) {
                        //文件夹
                        val fileInfo = FileInfo()
                        fileInfo.fileName = f.name
                        fileInfo.lastUpdateTime = TimeUtil.getDateInString(Date(f.lastModified()))
                        fileInfo.fileType = FileInfo.FileType.Folder
                        fileInfo.filePath = f.path
                        fileInfo.setFileCount(getSubfolderNum(f.path).toLong())
                        fileInfo.accessType = FileInfo.judgeAccess(f.path)
                        fileInfoList.add(fileInfo)
                    } else if (!FileSelectOptions.getInstance().isSelectDirectory) {
                        if (FileSelectOptions.getInstance().isUseFilter) {
                            if (!fileFilter(
                                    f.path,
                                    *FileSelectOptions.getInstance().fileTypeFilter
                                )
                            ) continue
                        }

                        FileInfo().apply {
                            fileType = when {
                                isAudioFileType(f.path) -> FileInfo.FileType.Audio
                                isImageFileType(f.path) -> FileInfo.FileType.Image
                                isVideoFileType(f.path) -> FileInfo.FileType.Video
                                isTextFileType(f.path) -> FileInfo.FileType.Text
                                isZipFileType(f.path) -> FileInfo.FileType.Zip
                                else -> FileInfo.FileType.Unknown
                            }

                            fileName = f.name
                            filePath = f.path
                            lastUpdateTime = TimeUtil.getDateInString(Date(f.lastModified()))
                            setFileCount(f.length())
                            accessType = FileInfo.judgeAccess(f.path)
                            fileInfoList.add(this)
                        }
                    }
                }
            }
        }

    private fun getProtectedFileList() {
        fileInfoList.clear()
        for (file in protectedFileList) {
            file.name ?: continue
            if (file.name!!.indexOf(".") == 0) continue

            Logger.debug("Android保护文件 ${file.name!!}")
            if (file.isDirectory) {
                val fileInfo = FileInfo()
                fileInfo.fileName = file.name
                fileInfo.lastUpdateTime = TimeUtil.getDateInString(Date(file.lastModified()))
                fileInfo.fileType = FileInfo.FileType.Folder
                fileInfo.accessType = FileInfo.AccessType.Protected
                fileInfo.filePath = changeToPath(file.uri.toString())
                fileInfo.setFileCount(getSubFolderNum(file).toLong())
                fileInfoList.add(fileInfo)
            } else {
                val path = changeToPath(file.uri.toString())
                if (FileSelectOptions.getInstance().isUseFilter) {
                    if (!fileFilter(
                            path!!,
                            *FileSelectOptions.getInstance().fileTypeFilter
                        )
                    ) continue
                }

                FileInfo().apply {
                    fileType = when {
                        isAudioFileType(path!!) -> FileInfo.FileType.Audio
                        isImageFileType(path) -> FileInfo.FileType.Image
                        isVideoFileType(path) -> FileInfo.FileType.Video
                        isTextFileType(path) -> FileInfo.FileType.Text
                        else -> FileInfo.FileType.Unknown
                    }

                    fileName = file.name
                    lastUpdateTime = TimeUtil.getDateInString(Date(file.lastModified()))
                    filePath = path
                    setFileCount(file.length())
                    accessType = FileInfo.AccessType.Protected
                    fileInfoList.add(this)
                }
            }
        }
    }

    override fun run() {
        super.run()
        when (accessType) {
            FileInfo.AccessType.Open -> openFileList
            FileInfo.AccessType.Protected -> getProtectedFileList()
        }
        callback(PROCESS_DONE, fileInfoList)
        if (countDownLatch != null) countDownLatch!!.countDown()
    }

    class FileListHandler(private val listener: FileListListener) : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == PROCESS_DONE) {
                listener.onFileListGenerated(msg.obj as ArrayList<FileInfo>)
            }
        }
    }

    interface FileListListener {
        fun onFileListGenerated(fileInfoList: ArrayList<FileInfo>?)
    }

    companion object {
        const val PROCESS_DONE = 1001
        private val Logger = LoggerFactory.getLogger("ListFile")
    }
}

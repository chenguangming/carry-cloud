package com.photons.carrycloud.utils

import android.annotation.TargetApi
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.photons.carrycloud.App
import com.photons.carrycloud.localfile.objects.FileInfo
import com.photons.carrycloud.ui.fileselector.FileSelectOptions
import java.io.*
import java.text.Collator
import java.text.DecimalFormat
import java.util.*


object FileUtils {
    private const val TAG = "FileUtils"
    private const val SIZE_K = 1024.0
    private const val SIZE_M = SIZE_K * SIZE_K
    private const val SIZE_G = SIZE_M * SIZE_K

    var sFileExtensions: String? = null

    // Audio
    private const val FILE_TYPE_MP3 = 1
    private const val FILE_TYPE_M4A = 2
    private const val FILE_TYPE_WAV = 3
    private const val FILE_TYPE_AMR = 4
    private const val FILE_TYPE_AWB = 5
    private const val FILE_TYPE_WMA = 6
    private const val FILE_TYPE_OGG = 7
    private const val FIRST_AUDIO_FILE_TYPE = FILE_TYPE_MP3
    private const val LAST_AUDIO_FILE_TYPE = FILE_TYPE_OGG

    // MIDI
    private const val FILE_TYPE_MID = 11
    private const val FILE_TYPE_SMF = 12
    private const val FILE_TYPE_IMY = 13
    private const val FIRST_MIDI_FILE_TYPE = FILE_TYPE_MID
    private const val LAST_MIDI_FILE_TYPE = FILE_TYPE_IMY

    // Video
    private const val FILE_TYPE_MP4 = 21
    private const val FILE_TYPE_M4V = 22
    private const val FILE_TYPE_3GPP = 23
    private const val FILE_TYPE_3GPP2 = 24
    private const val FILE_TYPE_WMV = 25
    private const val FIRST_VIDEO_FILE_TYPE = FILE_TYPE_MP4
    private const val LAST_VIDEO_FILE_TYPE = FILE_TYPE_WMV

    // Image
    private const val FILE_TYPE_JPEG = 31
    private const val FILE_TYPE_GIF = 32
    private const val FILE_TYPE_PNG = 33
    private const val FILE_TYPE_BMP = 34
    private const val FILE_TYPE_WBMP = 35
    private const val FIRST_IMAGE_FILE_TYPE = FILE_TYPE_JPEG
    private const val LAST_IMAGE_FILE_TYPE = FILE_TYPE_WBMP

    // Playlist
    private const val FILE_TYPE_M3U = 41
    private const val FILE_TYPE_PLS = 42
    private const val FILE_TYPE_WPL = 43
    private const val FIRST_PLAYLIST_FILE_TYPE = FILE_TYPE_M3U
    private const val LAST_PLAYLIST_FILE_TYPE = FILE_TYPE_WPL

    //TEXT
    private const val FILE_TYPE_TXT = 51
    private const val FILE_TYPE_DOC = 52
    private const val FILE_TYPE_RTF = 53
    private const val FILE_TYPE_LOG = 54
    private const val FILE_TYPE_CONF = 55
    private const val FILE_TYPE_SH = 56
    private const val FILE_TYPE_XML = 57

    private const val FILE_TYPE_ZIP = 58
    private const val FIRST_TEXT_FILE_TYPE = FILE_TYPE_TXT
    private const val LAST_TEXT_FILE_TYPE = FILE_TYPE_XML

    private const val FILE_PROVIDER_PREFIX = "storage_root"
    private const val LEGACY_EXTERNAL_STORAGE_PREFIX = "/storage/emulated/0"
    private const val EXTERNAL_STORAGE_PREFIX =
        "content://com.android.externalstorage.documents/tree/primary%3A"
    private const val SEG_ANDROID = "Android%2F"
    private const val LEGACY_SEG_ANDROID = "Android"

    //静态内部类
    class MediaFileType(var fileType: Int, var mimeType: String)

    private val sFileTypeMap = HashMap<String, MediaFileType>()
    private val sMimeTypeMap = HashMap<String, Int>()
    private fun addFileType(extension: String, fileType: Int, mimeType: String) {
        sFileTypeMap[extension] = MediaFileType(fileType, mimeType)
        sMimeTypeMap[mimeType] = fileType
    }

    init {
        addFileType("MP3", FILE_TYPE_MP3, "audio/mpeg")
        addFileType("M4A", FILE_TYPE_M4A, "audio/mp4")
        addFileType("WAV", FILE_TYPE_WAV, "audio/x-wav")
        addFileType("AMR", FILE_TYPE_AMR, "audio/amr")
        addFileType("AWB", FILE_TYPE_AWB, "audio/amr-wb")
        addFileType("WMA", FILE_TYPE_WMA, "audio/x-ms-wma")
        addFileType("OGG", FILE_TYPE_OGG, "application/ogg")

        addFileType("MID", FILE_TYPE_MID, "audio/midi")
        addFileType("XMF", FILE_TYPE_MID, "audio/midi")
        addFileType("RTTTL", FILE_TYPE_MID, "audio/midi")
        addFileType("SMF", FILE_TYPE_SMF, "audio/sp-midi")
        addFileType("IMY", FILE_TYPE_IMY, "audio/imelody")

        addFileType("MP4", FILE_TYPE_MP4, "video/mp4")
        addFileType("M4V", FILE_TYPE_M4V, "video/mp4")
        addFileType("3GP", FILE_TYPE_3GPP, "video/3gpp")
        addFileType("3GPP", FILE_TYPE_3GPP, "video/3gpp")
        addFileType("3G2", FILE_TYPE_3GPP2, "video/3gpp2")
        addFileType("3GPP2", FILE_TYPE_3GPP2, "video/3gpp2")
        addFileType("WMV", FILE_TYPE_WMV, "video/x-ms-wmv")

        addFileType("JPG", FILE_TYPE_JPEG, "image/jpeg")
        addFileType("JPEG", FILE_TYPE_JPEG, "image/jpeg")
        addFileType("GIF", FILE_TYPE_GIF, "image/gif")
        addFileType("PNG", FILE_TYPE_PNG, "image/png")
        addFileType("BMP", FILE_TYPE_BMP, "image/x-ms-bmp")
        addFileType("WBMP", FILE_TYPE_WBMP, "image/vnd.wap.wbmp")

        addFileType("M3U", FILE_TYPE_M3U, "audio/x-mpegurl")
        addFileType("PLS", FILE_TYPE_PLS, "audio/x-scpls")
        addFileType("WPL", FILE_TYPE_WPL, "application/vnd.ms-wpl")

        addFileType("TXT", FILE_TYPE_TXT, "text/plain")
        addFileType("DOC", FILE_TYPE_DOC, "application/msword")
        addFileType("RTF", FILE_TYPE_RTF, "application/rtf")
        addFileType("LOG", FILE_TYPE_LOG, "text/plain")
        addFileType("CONF", FILE_TYPE_CONF, "text/plain")
        addFileType("SH", FILE_TYPE_SH, "text/plain")
        addFileType("XML", FILE_TYPE_XML, "text/plain")
        addFileType("ZIP", FILE_TYPE_ZIP, "application/zip")

        // compute file extensions list for native Media Scanner
        val builder = StringBuilder()
        sFileTypeMap.keys.forEach {
            if (builder.isNotEmpty()) {
                builder.append(',')
            }
            builder.append(it)
        }

        sFileExtensions = builder.toString()
    }

    fun isAudioFileType(fileType: Int): Boolean {
        return (fileType in FIRST_AUDIO_FILE_TYPE..LAST_AUDIO_FILE_TYPE || fileType >= FIRST_MIDI_FILE_TYPE) && fileType <= LAST_MIDI_FILE_TYPE
    }

    fun isVideoFileType(fileType: Int): Boolean {
        return fileType in FIRST_VIDEO_FILE_TYPE..LAST_VIDEO_FILE_TYPE
    }

    fun isImageFileType(fileType: Int): Boolean {
        return fileType in FIRST_IMAGE_FILE_TYPE..LAST_IMAGE_FILE_TYPE
    }

    fun isPlayListFileType(fileType: Int): Boolean {
        return fileType in FIRST_PLAYLIST_FILE_TYPE..LAST_PLAYLIST_FILE_TYPE
    }

    fun isTextFileType(fileType: Int): Boolean {
        return fileType in FIRST_TEXT_FILE_TYPE..LAST_TEXT_FILE_TYPE
    }

    fun isZipFileType(fileType: Int): Boolean {
        return fileType == FILE_TYPE_ZIP
    }

    private fun getFileType(path: String): MediaFileType? {
        val lastDot = path.lastIndexOf(".")
        return if (lastDot < 0) null else sFileTypeMap[path.substring(lastDot + 1)
            .uppercase(Locale.getDefault())]
    }

    //根据视频文件路径判断文件类型
    fun isVideoFileType(path: String): Boolean {
        val type = getFileType(path)
        return if (null != type) {
            isVideoFileType(type.fileType)
        } else false
    }

    //根据音频文件路径判断文件类型
    fun isAudioFileType(path: String): Boolean {
        val type = getFileType(path)
        return if (null != type) {
            isAudioFileType(type.fileType)
        } else false
    }

    //根据图片文件路径判断文件类型
    fun isImageFileType(path: String): Boolean {
        val type = getFileType(path)
        return if (null != type) {
            isImageFileType(type.fileType)
        } else false
    }

    //根据文本文件路径判断文件类型
    fun isTextFileType(path: String): Boolean {
        val type = getFileType(path)
        return if (null != type) {
            isTextFileType(type.fileType)
        } else false
    }

    fun isZipFileType(path: String): Boolean {
        val type = getFileType(path)
        return if (null != type) {
            isZipFileType(type.fileType)
        } else false
    }

    //根据mime类型查看文件类型
    fun getFileTypeForMimeType(mimeType: String): Int {
        val value = sMimeTypeMap[mimeType]
        return value ?: 0
    }

    fun getSubfolderNum(path: String): Int {
        var i = 0
        val files = File(path).listFiles() ?: return -1
        for (f in files) {
            if (f.name.indexOf(".") != 0) {
                i++
            }
        }
        return i
    }

    fun getSubFolderNum(documentFile: DocumentFile): Int {
        var i = 0
        val documentFiles = documentFile.listFiles()
        for (file in documentFiles) {
            if (file.name == null) continue
            if (file.name!!.indexOf(".") != 0) {
                i++
            }
        }
        return i
    }

    fun getFileSize(size: Long): String {
        val bytes = StringBuffer()
        val format = DecimalFormat("###.0")
        if (size >= SIZE_G) {
            bytes.append(format.format(size / SIZE_G)).append("GB")
        } else if (size >= SIZE_M) {
            bytes.append(format.format(size / SIZE_M)).append("MB")
        } else if (size >= SIZE_K) {
            bytes.append(format.format(size / SIZE_K)).append("KB")
        } else {
            if (size <= 0) {
                bytes.append("0B")
            } else {
                bytes.append(size.toInt()).append("B")
            }
        }
        return bytes.toString()
    }

    fun sortFilesByName(fileList: MutableList<FileInfo>) {
        fileList.sortWith { o1: FileInfo, o2: FileInfo ->
            // 文件在上，文件夹在下
            when {
                o1.isDirectory && !o2.isDirectory -> 1
                !o1.isDirectory && o2.isDirectory -> -1
                else -> Collator.getInstance().compare(o1.fileName, o2.fileName)
            }
        }
    }

    fun getRelativePaths(path: String): ArrayList<String> {
        val paths = ArrayList<String>()
        if (path.contains(FileSelectOptions.BasicPath)) {
            val startIndex: Int =
                path.indexOf(FileSelectOptions.BasicPath) + FileSelectOptions.BasicPath.length
            val rawPath = path.substring(startIndex)
            val p = rawPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            for (l in p) {
                if (l != "") paths.add(l)
            }
        }
        return paths
    }

    fun fileFilter(path: String, vararg extensions: String): Boolean {
        val pathUppercase = path.uppercase(Locale.getDefault())
        for (extension in extensions) {
            val ext = extension.uppercase(Locale.getDefault())
            if (pathUppercase.endsWith(ext)) return true
        }
        return false
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun mergeAbsolutePath(paths: ArrayList<String>): String {
        return FileSelectOptions.BasicPath + File.separator + java.lang.String.join("/", paths)
    }

    //判断是否已经获取了受保护的文件夹的访问权限
    fun isProtectedFileGranted(context: Context, path: String): Boolean {
        val uri = changeToUriNormal(path)
        for (persistedUriPermission in context.contentResolver.persistedUriPermissions) {
            if (persistedUriPermission.isReadPermission && persistedUriPermission.uri.toString() == uri) {
                return true
            }
        }
        return false
    }

    //将path转换成可解析的uri文本
    fun changeToUriAndroidOrigin(raw: String): String? {
        var path = raw
        if (path.endsWith("/")) {
            path = path.substring(0, path.length - 1)
        }

        val path2 = path.replace("$LEGACY_EXTERNAL_STORAGE_PREFIX/", "").replace("/", "%2F")
        return "${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}data/document/primary%3A$path2"
    }

    fun changeToUriNormal(raw: String): String {
        var path = raw.replace("$LEGACY_EXTERNAL_STORAGE_PREFIX/", "")
        path = Uri.encode(path).replace("/", "%2F")
        return "${EXTERNAL_STORAGE_PREFIX}$path"
    }

    fun changeToPath(uri: String): String? {
        var path = uri
        if (uri.contains("${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}data")) path =
            uri.replace(
                "${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}data/document/primary%3A",
                ""
            ).replace(
                "%2F",
                "/"
            ) else if (uri.contains("${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}obb")) path =
            uri.replace(
                "${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}obb/document/primary%3A",
                ""
            ).replace("%2F", "/")
        path = Uri.decode(path)
        return "/storage/emulated/0/$path"
    }

    fun getDocumentFilePath(context: Context, raw: String): DocumentFile? {
        var path = raw
        var pathPattern = LEGACY_EXTERNAL_STORAGE_PREFIX
        var rootUri = EXTERNAL_STORAGE_PREFIX
        if (path.contains("/$LEGACY_SEG_ANDROID/data")) {
            pathPattern = "$LEGACY_EXTERNAL_STORAGE_PREFIX/$LEGACY_SEG_ANDROID/data"
            rootUri = "${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}data"
        }
        if (path.contains("/$LEGACY_SEG_ANDROID/obb")) {
            pathPattern = "$LEGACY_EXTERNAL_STORAGE_PREFIX/$LEGACY_SEG_ANDROID/obb"
            rootUri = "${EXTERNAL_STORAGE_PREFIX}${SEG_ANDROID}obb"
        }
        //String treeUri = "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3AAndroid%2Fdata";
        var document = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
        path = path.replace(pathPattern, "")
        val parts = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        for (i in parts.indices) {
            if (parts[i] == "") continue
            val encodedPath = Uri.decode(parts[i])
            if (document == null) break
            document = document.findFile(encodedPath)
        }
        return document
    }

    fun canListFiles(f: File): Boolean {
        return f.canRead() && f.isDirectory
    }

    @TargetApi(Build.VERSION_CODES.N)
    fun getVolumeDirectory(volume: StorageVolume?): File {
        return try {
            val f = StorageVolume::class.java.getDeclaredField("mPath")
            f.isAccessible = true
            f[volume] as File
        } catch (e: java.lang.Exception) {
            // This shouldn't fail, as mPath has been there in every version
            throw RuntimeException(e)
        }
    }

    fun getAllFilesSize(file: File): Long {
        if (!file.exists()) {
            return 0
        }

        return if (file.isFile) {
            file.length()
        } else {
            var total = 0L
            file.listFiles()?.forEach {
                total += getAllFilesSize(it)
            }
            total
        }
    }

    fun copyFilesFromAssets(context: Context, assetsPath: String, savePath: String) {
        try {
            val fileNames: Array<String>? = context.assets.list(assetsPath)
            fileNames?.apply {
                if (isNotEmpty()) { // 如果是目录
                    val file = File(savePath)
                    file.mkdirs() // 如果文件夹不存在，则递归
                    for (fileName in fileNames) {
                        copyFilesFromAssets(
                            context, "$assetsPath/$fileName",
                            "$savePath/$fileName"
                        )
                    }
                } else { // 如果是文件
                    val `is`: InputStream = context.assets.open(assetsPath)
                    val fos = FileOutputStream(File(savePath))
                    val buffer = ByteArray(1024)
                    var byteCount = 0
                    while (`is`.read(buffer).also { byteCount = it } != -1) { // 循环从输入流读取
                        // buffer字节
                        fos.write(buffer, 0, byteCount) // 将读取的输入流写入到输出流
                    }
                    fos.flush() // 刷新缓冲区
                    `is`.close()
                    fos.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun copyFile(fromFile: File, toFile: File) {
        if (!fromFile.exists() || !fromFile.isFile || !fromFile.canRead()) {
            return
        }

        toFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

        if (toFile.exists()) {
            toFile.delete()
        }
        try {
            val fis = FileInputStream(fromFile)
            val fos = FileOutputStream(toFile)
            val bt = ByteArray(1024)
            var c: Int
            while (fis.read(bt).also { c = it } > 0) {
                fos.write(bt, 0, c)
            }
            fis.close()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteFile(file: File) {
        if (!file.exists()) {
            return
        }

        try {
            if (file.isDirectory) {
                val files = file.listFiles()
                if (files == null || files.isEmpty()) {
                    file.delete()
                    return
                }

                files.forEach {
                    if (it.isFile) {
                        it.delete()
                    } else if (it.isDirectory) {
                        deleteFile(it)
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fromContentUri(uri: Uri): File {
        val tag = "fromContentUri"

        if (uri.scheme?.startsWith("content://") != true) {
            Logger.w(tag, "URI must start with content://. URI was [$uri]")
        }
        var pathFile = File(uri.path!!.substring(FILE_PROVIDER_PREFIX.length + 1))
        if (!pathFile.exists()) {
            Logger.w(tag, "failed to navigate to path ${pathFile.path}")
            pathFile = File(uri.path!!)
            Logger.w(tag, "trying to navigate to path ${pathFile.path}")
        }
        return pathFile
    }

    fun saveToFile(path: String, content: String) {
        File(path).printWriter().use { out ->
            out.print(content)
        }
    }

    fun buildServerConfFile(context: Context, assetsPath: String, toFilePath: String) {
        val input = Scanner(context.assets.open(assetsPath))
        val writer = PrintWriter(toFilePath)
        var line: String
        // lib所在目录，属system组，具有可执行权限
        val bbRoot = context.applicationInfo.nativeLibraryDir
        // /sdcard/Android/data/com.photons.carrycloud/cache，属sdcard_rw组，权限及访问都受SAF限制，不是真正的Posix访问，无法建socket
        val ccRoot = App.instance.getRootPath()
        // /data/user/0/com.photons.carrycloud/cache，属u0_a105_cache组，真正的Posix访问，可以建socket
        val ddRoot = context.cacheDir.absolutePath

        while (input.hasNext()) {
            line = input.nextLine()
            // 将服务器配置文件中的路径占位符替换成APP运行时路径
            if (line.contains("bb_root_path"))
                line = line.replace("bb_root_path", bbRoot)

            if (line.contains("cc_root_path"))
                line = line.replace("cc_root_path", ccRoot)

            if (line.contains("dd_root_path"))
                line = line.replace("dd_root_path", ddRoot)

            if (line.contains("server_port"))
                line = line.replace("server_port", "${App.instance.getServerPort()}")

            writer.write(line)
            writer.write("\n")
        }
        input.close()
        writer.close()
    }
}
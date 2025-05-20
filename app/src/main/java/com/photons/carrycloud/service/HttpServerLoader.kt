package com.photons.carrycloud.service

import android.content.Context
import android.text.TextUtils
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import com.photons.carrycloud.task.ZipTask
import com.photons.carrycloud.utils.FileUtils
import com.photons.carrycloud.utils.Shell
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale
import java.util.UUID


class HttpServerLoader(private val context: Context, private val listener: LoaderListener) {
    companion object {
        private val Logger = LoggerFactory.getLogger("Loader")
    }

    private val binRoot = context.applicationInfo.nativeLibraryDir
    private var dataRoot: String = ""

    private var httpProcess: SubProcess? = null
    private var ddnsProcess: SubProcess? = null

    interface LoaderListener {
        fun onServerStarted()
        fun onServerStopped(errNo: Int)
    }

    private fun ensureDirectoryExists(path: String) {
        File(path).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun followSystemLanguage() {
        followSystemLanguage("$dataRoot/www/lang")
    }

    private fun followSystemLanguage(langPath: String) {
        val langFile = File(langPath)
        val locale = Locale.getDefault()
        var sysLang = locale.language

        if (sysLang.equals("zh")){
            Logger.debug("zh: ${locale.toLanguageTag()}")
            sysLang = if (locale.toLanguageTag() == "zh-Hans-CN" || locale.toLanguageTag() == "zh-CN") {
                "zh-CN"
            } else {
                "zh-TW"
            }
        }

        if (langFile.exists()) {
            if (sysLang.equals(langFile.readText())) {
                Logger.debug("same with lang $sysLang")
                return
            }
        }

        Logger.debug("save lang $sysLang to $langPath")
        FileUtils.saveToFile(langPath, sysLang)
    }

    fun prepare(dataRoot: String) {
        this.dataRoot = dataRoot

        // 配置文件很重要，为避免升级等造成运行时路径发生变化，每次都加载
        ensureDirectoryExists("$dataRoot/conf")
        FileUtils.buildServerConfFile(context, "conf/lighttpd.conf", "$dataRoot/conf/lighttpd.conf")
        FileUtils.buildServerConfFile(context, "conf/php.ini", "$dataRoot/conf/php.ini")

        if (File("$dataRoot/loaded").exists()) {
            followSystemLanguage("$dataRoot/www/lang")
            start()
            return
        }

        // 保证配置目录存在
        ensureDirectoryExists("$dataRoot/upload")
        ensureDirectoryExists("$dataRoot/www")
        ensureDirectoryExists("$dataRoot/tmp")

        followSystemLanguage("$dataRoot/www/lang")

        // 将kodbox从asset中拷贝到cache目录
        val serverPackage = File("${context.cacheDir}/kodbox.zip")
        if (!serverPackage.exists()) {
            FileUtils.copyFilesFromAssets(context, serverPackage.name, serverPackage.absolutePath)
        }

        val requestId = ZipTask.decompress("$dataRoot/www", serverPackage.absolutePath)
        WorkManager.getInstance(App.instance)
            // requestId is the WorkRequest id
            .getWorkInfoByIdLiveData(requestId)
            .observeForever{ workInfo: WorkInfo ->
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    if (!File("${dataRoot}/www/clipboard").exists()) {
                        FileUtils.copyFilesFromAssets(
                            context,
                            "clipboard",
                            "${dataRoot}/www/clipboard"
                        )
                    }

                    // only for test
                    FileUtils.copyFilesFromAssets(context, "conf/info.php", "$dataRoot/www/info.php")

                    // 已加载完成标志!!!避免反复加载
                    File("$dataRoot/loaded").createNewFile()
                }
            }

        LiveEventBus
            .get(Constants.NEED_WAITING, UUID::class.java)
            .post(requestId)
    }

    fun start() {
        if (TextUtils.isEmpty(dataRoot)) {
            return
        }

        if (!File("$dataRoot/upload").exists()) {
            File("$dataRoot/upload").mkdir()
        }

        if (httpProcess == null) {
            httpProcess = SubProcess(
                "lighttpd",
                "$binRoot/liblighttpd.so -f $dataRoot/conf/lighttpd.conf -D",
                arrayOf("LD_LIBRARY_PATH=$binRoot"),
                object : SubProcess.Listener {
                override fun onStarted() {
                    listener.onServerStarted()
                }

                override fun onStopped(exitValue: Int) {
                    Logger.debug("exitValue $exitValue")
                    listener.onServerStopped(exitValue)
                }

            })
            httpProcess?.run()
        }

        startDDNS()
    }

    fun stop() {
        httpProcess?.kill()

        // 保障子进程退出
        Shell.exec("killall -9 liblighttpd.so", null)

        httpProcess = null

        stopDDNS()
    }

    private fun startDDNS() {
        if (ddnsProcess != null) {
            return
        }

        ddnsProcess = SubProcess(
            "ddns-go",
            "$binRoot/libddns-go.so -c $dataRoot/conf/ddns_go_config.yaml",
            arrayOf("LD_LIBRARY_PATH=$binRoot"),
            object : SubProcess.Listener {
                override fun onStarted() {
                    Logger.debug("ddns-go onStarted")
                }

                override fun onStopped(exitValue: Int) {
                    Logger.debug("ddns-go exitValue $exitValue")
                }
            })
        ddnsProcess?.run()
    }

    private fun stopDDNS() {
        ddnsProcess?.kill()

        // 保障子进程退出
        Shell.exec("killall -9 libddns-go.so", null)

        ddnsProcess = null
    }
}
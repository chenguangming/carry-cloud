package com.photons.carrycloud

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.Constants.GLOBAL_IPV4
import com.photons.carrycloud.Constants.WS_PORT
import com.photons.carrycloud.net.NetManager
import com.photons.carrycloud.service.WebService
import com.photons.carrycloud.ui.home.HomeFragment
import com.photons.carrycloud.ui.home.HomeFragment.Companion
import com.photons.carrycloud.ui.notifications.MyWebSocketServer
import com.photons.carrycloud.ui.notifications.NotificationsViewModel
import com.photons.carrycloud.utils.SPUtils
import com.photons.carrycloud.utils.SPUtils.KEY_SERVER_PORT
import com.qmuiteam.qmui.arch.QMUISwipeBackActivityManager
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class App : Application() {
    companion object {
        private val Logger = LoggerFactory.getLogger("App")
        lateinit var instance: App
    }

    private lateinit var mainHandler: Handler

    private var wsServer: MyWebSocketServer? = null

    fun getRootPath(): String {
        // Android 11之后，sdcard下的文件都通过MediaStore API访问，包括Java的File类
        // 所有文件的所有者都是com.android.providers.media.module，经测试，php的flock会失败
        // 为保证完整的文件访问，使用应用的cache作为服务器根目录，避免被MediaStore限制
        return cacheDir.absolutePath
    }

//    fun getServerPath(): String {
//        return "http://${NetworkUtils.localIPv4}:${getServerPort()}"
//    }

//    fun getServerPathV6(): String {
//        val port = getServerPort()
//        val sb = StringBuilder()
//        NetworkUtils.localIPv6.forEach {
//            sb.append("http://[${it}]:$port\n")
//        }
//
//        return sb.toString()
//    }

    fun getServerPort(): Int {
        return SPUtils.getInt(KEY_SERVER_PORT, Constants.HTTP_PORT)
    }

    fun getServerRoot(): String {
        return "${getRootPath()}/www"
    }

    fun startServer(start: Boolean) {
        Logger.debug("startServer $start")
        Log.d("App", "startServer $start")
        if (start) {
            startForegroundService(Intent(this, WebService::class.java))
        } else {
            stopService(Intent(this, WebService::class.java))
        }
    }

    fun startWsServer() {
        val myHost = InetSocketAddress(GLOBAL_IPV4, WS_PORT)
        if (wsServer == null) {
            wsServer = MyWebSocketServer(myHost)
            wsServer?.isReuseAddr = true // 避免java.net.BindException:Address already in use异常
            wsServer?.start()
        }
    }

    fun bindViewModel(viewModel: NotificationsViewModel) {
        wsServer?.setViewModel(viewModel)
    }

    fun unbindViewModel() {
        wsServer?.unbindViewModel()
    }

    override fun onCreate() {
        super.onCreate()
        QMUISwipeBackActivityManager.init(this)
        SkinManager.install(this)

        instance = this
        mainHandler = Handler(Looper.getMainLooper())

        NetManager.listenNetwork()

        LiveEventBus
            .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
            .observeForever {
                Log.d("App", "NOTIFY_ACCESS_CHANGED $it")
                val addr = NetManager.getFirstAccessAddresses()
                if (NetManager.isReady()) {
                    toast(getString(R.string.network_ready, addr))

                    startServer(true)
                    startWsServer()
                } else {
                    toast(getString(R.string.no_network))
                    startServer(false)
                }
            }

        Logger.debug("onCreate")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Logger.debug("onConfigurationChanged $newConfig")

        LiveEventBus.get<String>(Constants.LANG_CHANGED).post(newConfig.locales[0].toString())

        if ((newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            SkinManager.changeSkin(SkinManager.SKIN_DARK)
        } else {
            SkinManager.changeSkin(SkinManager.SKIN_WHITE)
        }
    }

    fun changePort(port: Int) {
        SPUtils.putInt(KEY_SERVER_PORT, port)

        startServer(false)

        mainHandler.postDelayed({
            startServer(true)
            toast(getString(R.string.done))
        }, 1000)
    }

    fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}


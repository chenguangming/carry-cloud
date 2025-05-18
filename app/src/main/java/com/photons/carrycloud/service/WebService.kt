package com.photons.carrycloud.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.*
import com.photons.carrycloud.Constants.WORKER_PROGRESS_KEY
import com.photons.carrycloud.task.ZipTask
import com.photons.carrycloud.utils.NetworkUtils
import com.photons.carrycloud.utils.ProgressCallback
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference


class WebService : LifecycleService(), HttpServerLoader.LoaderListener {
    private var httpServerLoader: HttpServerLoader? = null
    private var wakeLock: WakeLock? = null

    companion object {
        private val Logger = LoggerFactory.getLogger("WebService")
        const val DEFAULT_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.notification"
        private const val CHANNEL_NAME = BuildConfig.APPLICATION_ID
        private const val FG_NOTIFICATION_ID = 1
        var isRunning = false
        var httpServerState: HttpServerState? = null
        var instance: WeakReference<WebService>? = null

        fun startHttpServer(on: Boolean) {
            if (on) {
                instance?.get()?.httpServerLoader?.start()
            } else {
                instance?.get()?.httpServerLoader?.stop()
            }
        }

        fun isHttpServerStart(): Boolean {
            return httpServerState?.isStarted == true
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = WeakReference(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WebService::class.java.simpleName)
        wakeLock?.acquire()

        isRunning = true
        val notifyMgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        registerNotificationChannel(notifyMgr)

        Logger.debug("onCreate")

        try {
            startForeground(FG_NOTIFICATION_ID, buildNotification(NetworkUtils.localIPv4))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        httpServerLoader = HttpServerLoader(this, this)
        prepareCloudRoot()

        LiveEventBus
            .get(Constants.NETWORK_STATE_CHANGED_KEY, String::class.java)
            .observeSticky(this) {
                Logger.debug("update ip $it")
                notifyMgr.notify(FG_NOTIFICATION_ID, buildNotification(it))
            }

        LiveEventBus
            .get(Constants.NOTIFY_PERMISSION_CHANGED_KEY, Boolean::class.java)
            .observeSticky(this) {
                Logger.debug("got notify permission $it")
                if (it) {
                    registerNotificationChannel(notifyMgr)
                    startForeground(FG_NOTIFICATION_ID, buildNotification(NetworkUtils.localIPv4))
                }
            }

        LiveEventBus
            .get(Constants.LANG_CHANGED, String::class.java)
            .observeSticky(this) {
                Logger.debug("check lang changed $it")
                httpServerLoader?.followSystemLanguage()
            }
    }

    private fun prepareCloudRoot() {
        val rootPath = App.instance.getRootPath()
        if (!TextUtils.isEmpty(rootPath) && rootPath.startsWith("/")) {
            Logger.debug("prepareCloudRoot $rootPath")

            httpServerLoader?.prepare(rootPath)
        }
    }

    fun exportCloudRoot(target: String, clientCallback: ProgressCallback?) {
        val reqId = ZipTask.compress(App.instance.getServerRoot(), target)
        WorkManager.getInstance(App.instance)
            // requestId is the WorkRequest id
            .getWorkInfoByIdLiveData(reqId)
            .observe(this) { workInfo: WorkInfo ->
                if (workInfo.state.isFinished) {
                    clientCallback?.onCompleted(workInfo.state == WorkInfo.State.SUCCEEDED)
                } else {
                    clientCallback?.onProgress(workInfo.progress.getInt(WORKER_PROGRESS_KEY, 0))
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()

        httpServerLoader?.stop()

        isRunning = false
        wakeLock?.release()
    }

    private fun buildNotification(localIP: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val nb = NotificationCompat.Builder(this, DEFAULT_CHANNEL_ID)
        nb.setContentIntent(PendingIntent.getActivity(this, 0, intent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE))
        nb.setContentTitle(getString(R.string.notification_title))
        nb.setContentText(getString(R.string.notification_content, "http://${localIP}:${App.instance.getServerPort()}"))
        nb.setSubText(getString(R.string.running))
        nb.setSilent(true)
        nb.setOngoing(true)
        nb.setAutoCancel(false)
        nb.setSmallIcon(R.mipmap.ic_launcher)
        return nb.build()
    }

    private fun registerNotificationChannel(notifyMgr: NotificationManager) {
        val notificationChannel = notifyMgr.getNotificationChannel(DEFAULT_CHANNEL_ID)
        if (notificationChannel == null) {
            val channel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            //是否在桌面icon右上角展示小红点
            channel.enableLights(false)
            //通知显示
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notifyMgr.createNotificationChannel(channel)
        }
    }

    override fun onServerStarted() {
        Logger.debug("on http server started")
        httpServerState = HttpServerState(true)

        LiveEventBus
            .get(Constants.HTTP_SERVER_CHANGED_KEY, HttpServerState::class.java)
            .post(httpServerState)
    }

    override fun onServerStopped(errNo: Int) {
        Logger.debug("on http server stopped")
        httpServerState = HttpServerState(errNo = errNo)

        LiveEventBus
            .get(Constants.HTTP_SERVER_CHANGED_KEY, HttpServerState::class.java)
            .post(httpServerState)
    }
}
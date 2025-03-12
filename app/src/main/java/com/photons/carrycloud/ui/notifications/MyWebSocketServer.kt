package com.photons.carrycloud.ui.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.photons.carrycloud.App
import com.photons.carrycloud.BuildConfig
import com.photons.carrycloud.Constants.PENDING_INTENT_PATH_NOTIFY
import com.photons.carrycloud.Constants.PENDING_INTENT_PATH_KEY
import com.photons.carrycloud.MainActivity
import com.photons.carrycloud.R
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.ArrayList
import kotlin.Exception

class MyWebSocketServer(host: InetSocketAddress): WebSocketServer(host) {
    private var notifyViewModel: NotificationsViewModel? = null
    private var notifyMgr = App.instance.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    private var conn: WebSocket? = null

    companion object {
        private val Logger = LoggerFactory.getLogger("WebSocket")
        private const val CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.Text"
        private const val CHANNEL_NAME = BuildConfig.APPLICATION_ID
        private const val NOTIFICATION_ID = 102
        val allMessage: ArrayList<BubbleData> = arrayListOf()
    }

    init {
        registerNotificationChannel(notifyMgr)
    }

    private val observer = Observer<String> {
        if (!TextUtils.isEmpty(it)) {
            try {
                conn?.send(it)
            } catch (e: Exception) {
                Logger.debug(e.message)
            }
            notifyViewModel?.sendStringData?.value = ""
        }
    }

    fun setViewModel(viewModel: NotificationsViewModel) {
        notifyViewModel = viewModel

        viewModel.sendStringData.observeForever(observer)
    }

    fun unbindViewModel() {
        notifyViewModel?.sendStringData?.removeObserver(observer)
        notifyViewModel = null
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Logger.debug("onOpen：" + conn.remoteSocketAddress)

        this.conn = conn
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Logger.debug("onClose")
        notifyViewModel?.onSystemMessageReceive("onClosed")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        Logger.debug("onMessage: $message")

        if (!TextUtils.isEmpty(message)) {
            val bubble = Gson().fromJson(message, BubbleData::class.java)
            bubble.id = System.currentTimeMillis() // 修改id为客户端时间戳

            notifyViewModel?.onMessageReceive(bubble)

            allMessage.add(bubble)

            notifyMgr.notify(NOTIFICATION_ID, buildNotification(bubble.msg))
        }
    }

    private fun buildNotification(msg: String): Notification {
        val context = App.instance
        val intent = Intent(context, MainActivity::class.java)
        intent.putExtra(PENDING_INTENT_PATH_KEY, PENDING_INTENT_PATH_NOTIFY)

        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setContentIntent(PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setContentTitle(context.getString(R.string.notification_text_share))
            setContentText(msg)
            setSilent(false)
            setSmallIcon(R.mipmap.ic_launcher)
        }.build()
    }

    private fun registerNotificationChannel(notifyMgr: NotificationManager) {
        notifyMgr.getNotificationChannel(CHANNEL_ID) ?: {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            //是否在桌面icon右上角展示小红点
            channel.enableLights(true)
            //通知显示
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notifyMgr.createNotificationChannel(channel)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        // 异常  经常调试的话会有缓存，导致下一次调试启动时，端口未关闭,多等待一会儿
        // 可以在这里回调处理，关闭连接，开个线程重新连接调用startMyWebsocketServer()
        Logger.error("onError：$ex")

        conn?.close()
        this.conn = null

        notifyViewModel?.onSystemMessageReceive("onError：$ex")
    }

    override fun onStart() {
        Logger.debug("websocket onStart")

        notifyViewModel?.onSystemMessageReceive("onStarted")
    }
}
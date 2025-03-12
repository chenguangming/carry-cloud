package com.photons.carrycloud.ui.notifications

import androidx.annotation.Keep

@Keep
data class BubbleData(
    val type: Int,
    val msg: String,
    var id: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 标识：系统消息
         */
        const val TYPE_SYSTEM = 0

        /**
         * 标识：发送方：服务端（远端）
         */
        const val TYPE_SERVER = 1

        /**
         * 标识：发送方：客户端（本机）
         */
        const val TYPE_CLIENT = 2
    }
}
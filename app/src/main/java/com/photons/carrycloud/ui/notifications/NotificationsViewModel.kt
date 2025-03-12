package com.photons.carrycloud.ui.notifications

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.photons.carrycloud.ui.notifications.BubbleData.Companion.TYPE_SYSTEM

class NotificationsViewModel : ViewModel() {

    /**
     * 接收到的字符串数据
     */
    val receiveMessage = MutableLiveData<BubbleData>()

    /**
     * 要发送的字符串数据
     */
    val sendStringData = MutableLiveData<String>()

    fun onMessageReceive(message: BubbleData) {
        receiveMessage.postValue(message)
    }

    fun onSystemMessageReceive(data: String) {
        receiveMessage.postValue(BubbleData(
            TYPE_SYSTEM,
            data
        ))
    }
}
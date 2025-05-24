package com.photons.carrycloud.net

import com.photons.bus.LiveEventBus
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import java.net.InetAddress

abstract class BaseMdns {
    val MDNS_SERVICE_NAME: String = "cc"
    val MDNS_SERVICE_DESC: String = "carrycloud"

    abstract fun start(address: InetAddress)
    abstract fun stop()

    private fun getFullUrl(name: String): String {
        return "http://$name.local:${Constants.HTTP_PORT}"
    }

    fun onDiscoveryMySelf(hostName: String) {
        NetManager.updateAccessAddress(Constants.ACCESS_TYPE_MDNS, getFullUrl(hostName))

        LiveEventBus
            .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
            .post(hostName)
    }
}
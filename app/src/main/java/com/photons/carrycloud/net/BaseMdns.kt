package com.photons.carrycloud.net

import com.photons.bus.LiveEventBus
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import java.net.InetAddress

abstract class BaseMdns {
    val MDNS_SERVICE_NAME: String = "cc"
    val MDNS_SERVICE_DESC: String = "carrycloud"
    var myAddress: String? = null

    abstract fun start(address: String)
    abstract fun stop()

    private fun getDomain(name: String): String {
        return "$name.local"
    }

    fun onDiscoveryMySelf(address: String, hostName: String) {
        NetManager.updateDnsResolved(Constants.ACCESS_TYPE_MDNS, address, getDomain(hostName))

        LiveEventBus
            .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
            .post(hostName)
    }
}
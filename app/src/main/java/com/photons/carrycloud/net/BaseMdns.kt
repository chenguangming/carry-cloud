package com.photons.carrycloud.net

import com.photons.bus.LiveEventBus
import com.photons.carrycloud.Constants

abstract class BaseMdns {
    val MDNS_SERVICE_NAME: String = "cc"
    val MDNS_SERVICE_DESC: String = "carrycloud"
    var myAddress: String? = null

    abstract fun start(address: String)
    abstract fun stop()

    private fun getDomain(name: String): String {
        if (name.endsWith('.')) {
            // jmdns 看到的域名是 cc-1.local. 这样的，实测要把最后的.去掉
            return name.substring(0, name.lastIndexOf('.'))
        }

        return name
    }

    fun onDiscoveryMySelf(address: String, hostName: String) {
        NetManager.updateDnsResolved(Constants.ACCESS_TYPE_MDNS, address, getDomain(hostName))

        LiveEventBus
            .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
            .post(hostName)
    }
}
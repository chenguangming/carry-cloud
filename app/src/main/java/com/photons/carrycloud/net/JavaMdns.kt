package com.photons.carrycloud.net

import android.util.Log
import com.photons.carrycloud.Constants.HTTP_PORT
import com.photons.carrycloud.utils.NetworkUtils
import com.photons.carrycloud.utils.NetworkUtils.formatAddress
import java.io.IOException
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener


object JavaMdns: ServiceListener, BaseMdns() {
    private val TAG = "JavaMdns"
    private var jmdns: JmDNS? = null

    override fun start(address: InetAddress) {
        Log.d(TAG, "JmDNS start: $address")
        try {
            // 使用自定义主机名初始化 JmDNS
            jmdns = JmDNS.create(address, MDNSS_SERVICE_NAME) // 明确指定主机名
            Log.d(TAG, "JmDNS initialized with IP: $address")

            // 创建 ServiceInfo
            val serviceInfo = ServiceInfo.create(
                MDNSS_SERVICE_TYPE,
                MDNSS_SERVICE_NAME,
                HTTP_PORT,
                MDNSS_SERVICE_DESC
            )

            // 注册服务
            jmdns?.addServiceListener(MDNSS_SERVICE_TYPE, this)
            jmdns?.registerService(serviceInfo)
            Log.d(TAG, "Service registered: $serviceInfo")
        } catch (e: IOException) {
            Log.e(TAG, "${e.message}")
        }
    }

    override fun stop() {
        // Unregister all services
        jmdns?.unregisterAllServices()
        jmdns = null
    }

    override fun serviceAdded(event: ServiceEvent) {
        Log.d(TAG, "Service added: " + event.info)
    }

    override fun serviceRemoved(event: ServiceEvent) {
        Log.d(TAG, "Service removed: " + event.info)
    }

    override fun serviceResolved(event: ServiceEvent) {
        Log.d(TAG, "Service resolved: " + event.info)
        Log.d(TAG, "Service resolved name: " + event.name)
        Log.d(TAG, "Service resolved addr: " + formatAddress(event.dns.inetAddress))
        if (event.dns.inetAddress.hostAddress?.equals(NetworkUtils.localIPv4) == true) {
            Log.d(TAG, "Service resolved myself")
            onDiscoveryMySelf(event.name)
        }
    }
}
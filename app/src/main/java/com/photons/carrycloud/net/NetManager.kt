package com.photons.carrycloud.net

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import com.photons.carrycloud.R
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap


class NetInfo(val network: Network, val type:Int) {
    private val TAG = "NetManager.NetInfo"
    val accessMap = ConcurrentHashMap<Int, String>()
    var firstAccess = 0

    // 更新某种访问类型的地址，如ipv4地址、mdns域名等
    fun updateAccessAddress(accessType: Int, address: String) {
        if (type == NetManager.NET_TYPE_CELLULAR && accessType == Constants.ACCESS_TYPE_IPV4) {
            Log.d(TAG, "cellular network can't accessed by ipv4")
            return
        }

        accessMap[accessType] = address

        updateFirstAccessType()
        Log.d(TAG, "type $type firstAccess $firstAccess")
    }

    fun getFirstAccessType(): Int {
        return firstAccess
    }

    // 更新首选访问类型
    private fun updateFirstAccessType() {
        for (i in Constants.ACCESS_TYPE_MDNS .. Constants.ACCESS_TYPE_IPV6) {
            if (accessMap[i] != null) {
                firstAccess = i
                return
            }
        }
    }
}

object NetManager {
    private val TAG = "NetManager"
    val NET_TYPE_NO_CONNECTED = -2
    val NET_TYPE_NO_CARE = -1
    val NET_TYPE_ETHERNET = 0
    val NET_TYPE_WIFI = 1
    val NET_TYPE_CELLULAR = 2
    private val addrReg = Regex("[.:]")
    /*  一台设备往往有多张网卡，每张网卡都有多个访问地址，如域名访问、ip地址访问，后者本身就有多个
        根据网络类型的优先级来保存网卡信息，每个网卡信息内部，又以mdns>ipv4>ddns>ipv6的顺序保存不同方式的访问地址
        ethernet -> [mdns, ipv4, ddns, ipv6]
        wifi -> [mdns, ipv4, ddns, ipv6]
        cellular -> [mdns, ipv4, ddns, ipv6]
     */
    private val linkNetworks = ConcurrentHashMap<Int, NetInfo>()
    val connManager = App.instance.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager
    var mdns = JavaMdns// AndroidMdns 注册后，无论怎么设置serviceName，实际域名都是android.local，所以暂时弃用
    var firstNetIndex = NET_TYPE_ETHERNET

    fun isReady(): Boolean {
        return linkNetworks.isNotEmpty()
    }

    fun isIPv4(address: InetAddress): Boolean {
        return (address is Inet4Address)
    }

    fun isIPv6(address: InetAddress): Boolean {
        return (address is Inet6Address) && !address.isLinkLocalAddress && !address.isAnyLocalAddress
    }

    fun isIPv6(address: String): Boolean {
        return address.contains("[")
    }

    // 获取首选网络的首选地址
    fun getFirstAccessAddresses():String {
        linkNetworks[firstNetIndex]?.apply {
            if (firstAccess == Constants.ACCESS_TYPE_IPV6) {
                return App.instance.getString(R.string.ipv6_no_dns)
            }

            return getServerPath(accessMap[firstAccess], firstAccess)
        }

        return getServerPath(null, Constants.ACCESS_TYPE_IPV4)
    }

    // DNS 解析后，需要更新目标网卡的访问列表
    fun updateDnsResolved(type: Int, ip: String, domainName: String) {
        linkNetworks.forEach { (_, netInfo) ->
            netInfo.accessMap.forEach { (_, address) ->
                if (ip == address) {
                    netInfo.updateAccessAddress(type, domainName)
                }
            }
        }
    }

    fun formatAddress(address: InetAddress): String? {
        return address.hostAddress?.let {
            return it.replace(addrReg, "-")
        }
    }

    fun checkPortAvailable(port: Int): Boolean {
        var serverSocket: ServerSocket? = null
        return try {
            serverSocket = ServerSocket(port)
            (serverSocket.isBound && !serverSocket.isClosed)
        } catch (e: Exception) {
            false
        } finally {
            serverSocket?.close()
        }
    }

    fun getAllAccessAddresses():String {
        linkNetworks[firstNetIndex]?.apply {
            val sb = StringBuilder()
            accessMap.forEach { (accessType, addr) ->
                sb.append("[").append(accessType).append("] ").append(getServerPath(addr, accessType)).append("\n")
            }
            return sb.toString()
        }

        return ""
    }

    fun addNetwork(network: Network): NetInfo {
        val type = getNetworkType(network)
        return NetInfo(network, type).apply {
            linkNetworks[type] = this
        }
    }

    fun getNetTypeName(type: Int): String {
        return when(type) {
            NET_TYPE_ETHERNET -> "ETHERNET"
            NET_TYPE_WIFI -> "WIFI"
            NET_TYPE_CELLULAR -> "CELLULAR"
            else -> "UNKOWN"
        }
    }

    fun selectNetwork(): NetInfo? {
        for (n in NET_TYPE_ETHERNET..NET_TYPE_CELLULAR) {
            linkNetworks[n]?.let {
                if (it.accessMap.isNotEmpty()) {
                    firstNetIndex = n
                    Log.d(TAG, "selectNetwork ${getNetTypeName(n)}")
                    return it
                }
            }
        }

        return null
    }

    fun removeNetwork(network: Network) {
        for (n in NET_TYPE_ETHERNET..NET_TYPE_CELLULAR) {
            linkNetworks[n]?.let {
                if (it.network == network) {
                    it.accessMap.clear()
                    linkNetworks.remove(n)
                    Log.d(TAG, "removeNetwork $n")
                    return
                }
            }
        }
    }


    fun getServerPath(ip: String?, accessType: Int): String {
        return if (accessType == Constants.ACCESS_TYPE_IPV6)
            "http://[${ip?:"::"}]:${App.instance.getServerPort()}"
        else
            "http://${ip?:"0.0.0.0"}:${App.instance.getServerPort()}"
    }

    fun listenNetwork() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()

        connManager.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                removeNetwork(network)

                selectNetwork() ?: mdns.stop()

                Log.d(TAG, "onLost type $network left links size ${linkNetworks.size}")
                LiveEventBus
                    .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
                    .post(("lost $network"))
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Log.d(TAG, "onLinkPropertiesChanged $network connManager  linkAddresses ${linkProperties.linkAddresses.size}")

                val info = addNetwork(network)
                linkProperties.linkAddresses.forEach { link ->
                    link.address.apply {
                        Log.d(TAG, "${linkProperties.interfaceName} linkAddresses: ${formatAddress(this)}")
                        if (isLoopbackAddress) {
                            return@apply
                        }

                        if (isIPv4(this)) {
                            info.updateAccessAddress(Constants.ACCESS_TYPE_IPV4, hostAddress!!)
                        } else if (isIPv6(this)) {
                            info.updateAccessAddress(Constants.ACCESS_TYPE_IPV6, hostAddress!!)
                        }
                    }
                }

                selectNetwork()?.let {
                    val accessType = it.getFirstAccessType()
                    // todo 当前首选网络的首选访问是IPV4时，启动MDNS，理论上IPV6也可以使用MDNS
                    if (accessType == Constants.ACCESS_TYPE_IPV4) {
                        mdns.start(it.accessMap[accessType]!!)
                    }
                }

                LiveEventBus
                    .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
                    .post(("changed ${connManager.getLinkProperties(network)?.interfaceName}"))
            }
        })
    }

    private fun getNetworkType(network: Network): Int {
        // 获取网络能力
        val networkCapabilities = connManager.getNetworkCapabilities(network)
        if (networkCapabilities == null) {
            Log.d(TAG, "No network $network capabilities")
            return NET_TYPE_NO_CONNECTED
        }

        // 判断网络类型
        when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                Log.d(TAG, "skip VPN network: $network")
                return NET_TYPE_NO_CARE
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                Log.d(TAG, "ethernet network: $network")
                return NET_TYPE_ETHERNET
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                Log.d(TAG, "wifi network: $network")
                return NET_TYPE_WIFI
            }
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                Log.d(TAG, "cellular network: $network")
                return NET_TYPE_CELLULAR
            }
            else -> {
                Log.d(TAG, "skip other network: $network")
                return NET_TYPE_NO_CARE
            }
        }
    }
}
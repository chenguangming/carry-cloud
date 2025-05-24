package com.photons.carrycloud.net

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.text.TextUtils
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

object NetManager {
    private val TAG = "NetManager"
    private val NET_TYPE_NO_CONNECTED = -2
    private val NET_TYPE_NO_CARE = -1
    private val NET_TYPE_ETHERNET = 0
    private val NET_TYPE_WIFI = 1
    private val NET_TYPE_CELLULAR = 2
    private val addrReg = Regex("[.:]")
    private val links = ConcurrentHashMap<Network, Int>()
    private val accessAddresses = ConcurrentHashMap<Int, String>()
    val connManager = App.instance.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager
    var mdns = JavaMdns// AndroidMdns 注册后，无论怎么设置serviceName，实际域名都是android.local，所以暂时弃用
    var fistAccessAddressIndex = Constants.ACCESS_TYPE_IPV4

    fun isReady(): Boolean {
        return links.isNotEmpty()
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

    fun getFirstAccessAddresses():String {
        if (fistAccessAddressIndex == Constants.ACCESS_TYPE_IPV6) {
            return App.instance.getString(R.string.ipv6_no_dns)
        }

        return accessAddresses[fistAccessAddressIndex] ?: Constants.GLOBAL_IPV4
    }

    private fun updateFirstAccessAddresses() {
        for (i in Constants.ACCESS_TYPE_MDNS .. Constants.ACCESS_TYPE_IPV6) {
            if (TextUtils.isEmpty(accessAddresses[i])) {
                continue
            }

            fistAccessAddressIndex = i
            return
        }

        fistAccessAddressIndex = 0
    }

    fun updateAccessAddress(type: Int, address: String) {
        accessAddresses[type] = address

        updateFirstAccessAddresses()
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
        val sb = StringBuilder()
        accessAddresses.forEach { (t, u) ->
            sb.append("[").append(t).append("] ").append(u).append("\n")
        }
        return sb.toString()
    }

    fun getServerPath(ip: String?): String {
        return "http://${ip?:"0.0.0.0"}:${App.instance.getServerPort()}"
    }

    fun getServerPathV6(ip: String?): String {
        return "http://[${ip?:"::"}]:${App.instance.getServerPort()}"
    }

    fun listenNetwork() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()

        connManager.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)

                links.remove(network)
                if (links.isEmpty()) {
                    accessAddresses.clear()
                    mdns.stop()
                }

                Log.d(TAG, "onLost type $network left links size ${links.size}")
                LiveEventBus
                    .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
                    .post(("lost $network"))
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Log.d(TAG, "onLinkPropertiesChanged $network connManager  linkAddresses ${linkProperties.linkAddresses.size}")

                val type = getNetworkType(network)
                if (type != NET_TYPE_NO_CARE) {
                    links[network] = type

                    linkProperties.linkAddresses.forEach { link ->
                        link.address.apply {
                            Log.d(TAG, "${linkProperties.interfaceName} linkAddresses: ${formatAddress(this)}")
                            if (isLoopbackAddress) {
                                return@apply
                            }

                            if (isIPv4(this)) {
                                if (type != NET_TYPE_CELLULAR) {
                                    updateAccessAddress(Constants.ACCESS_TYPE_IPV4, getServerPath(hostAddress!!))
                                    mdns.start(this)
                                }
                            } else if (isIPv6(this)) {
                                updateAccessAddress(Constants.ACCESS_TYPE_IPV6, getServerPathV6(hostAddress!!))
                            }
                        }
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
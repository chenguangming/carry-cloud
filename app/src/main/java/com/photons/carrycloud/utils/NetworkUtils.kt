package com.photons.carrycloud.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.photons.bus.LiveEventBus
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import com.photons.carrycloud.net.JavaMdns
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

object NetworkUtils {
    private val Logger = LoggerFactory.getLogger("NetworkUtils")
    private val networkMap = ConcurrentHashMap<Network, String>()
    private val addrReg = Regex("[.:]")
    var localIPv4 = Constants.GLOBAL_IPV4
    var localIPv6 = ArrayList<String>()


    fun isReady(): Boolean {
        return Constants.GLOBAL_IPV4 != localIPv4
    }

    fun isIPv4(address: InetAddress): Boolean {
        return (address is Inet4Address) || (address.hostAddress?.contains(".") == true)
    }

    fun isIPv6(address: InetAddress): Boolean {
        return (address is Inet6Address) && !address.isLinkLocalAddress
    }

    fun listenNetwork(context: Context) {
        val connManager = context.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager

        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()

        connManager.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                networkMap.remove(network)
                Logger.debug("onLost $network left ${networkMap.size}")

                if (networkMap.isEmpty()) {
                    onIpV4Changed(Constants.GLOBAL_IPV4)
                    onIpV6Changed(Constants.GLOBAL_IPV6)
                } else {
                    networkMap.forEach {
                        onIpV4Changed(it.value)
                    }
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Logger.debug("onLinkPropertiesChanged ${linkProperties.interfaceName}")

                linkProperties.linkAddresses.forEach { link ->
                    link.address.apply {
                        if (isLoopbackAddress) {
                            return@apply
                        }

                        if (isIPv4(this)) {
                            Logger.debug("add ipv4: ${formatAddress(this)}")

                            hostAddress?.let { ipv4 ->
                                networkMap[network] = ipv4
                                onIpV4Changed(ipv4)
                            }

                            JavaMdns.start(this)
                        } else if (isIPv6(this)) {
                            Logger.debug("add ipv6: ${formatAddressV6(this)} ")

                            hostAddress?.let { ipv6 ->
                                onIpV6Changed(ipv6)
                            }
                        }
                    }
                }
            }
        })
    }

    fun onIpV4Changed(ipv4: String) {
        if (localIPv4 != ipv4) {
            localIPv4 = ipv4
            App.instance.onNetworkChanged()
        }

        App.instance.accessAddresses[Constants.ACCESS_TYPE_IPV4] = "http://$ipv4:${Constants.HTTP_PORT}"
        LiveEventBus
            .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
            .post(ipv4)
    }

    fun onIpV6Changed(ipv6: String) {
        if (ipv6 == Constants.GLOBAL_IPV6) {
            localIPv6.clear()
        } else if (!localIPv6.contains(ipv6)) {
            localIPv6.add(ipv6)
        }
        App.instance.onIpv6Changed(ipv6)

        App.instance.accessAddresses[Constants.ACCESS_TYPE_IPV6] = "http://[$ipv6]:${Constants.HTTP_PORT}"
        LiveEventBus
            .get(Constants.NOTIFY_ACCESS_CHANGED_KEY, String::class.java)
            .post(ipv6)
    }

    fun formatAddress(address: InetAddress): String? {
        return address.hostAddress?.let {
            return it.replace(addrReg, "-")
        }
    }

    fun formatAddressV6(address: InetAddress): String? {
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
}
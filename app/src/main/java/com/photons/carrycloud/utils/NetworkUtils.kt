package com.photons.carrycloud.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

object NetworkUtils {
    private val Logger = LoggerFactory.getLogger("NetworkUtils")
    private val networkMap = ConcurrentHashMap<Network, String>()
    private val addrReg = Regex("[.:]")
    var localIP = Constants.GLOBAL_IP

    fun isReady(): Boolean {
        return Constants.GLOBAL_IP != localIP
    }

    fun isIPv4(address: InetAddress): Boolean {
        return (address is Inet4Address) || (address.hostAddress?.contains(".") == true)
    }

    fun listenNetwork(context: Context) {
        val connManager = context.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager

        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()

        connManager.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                networkMap.remove(network)
                Logger.debug("onLost $network left ${networkMap.size}")

                if (networkMap.isEmpty()) {
                    onIpChanged(Constants.GLOBAL_IP)
                } else {
                    networkMap.forEach {
                        onIpChanged(it.value)
                    }
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Logger.debug("onLinkPropertiesChanged ${linkProperties.interfaceName}")

                linkProperties.linkAddresses.forEach { link ->
                    link.address.apply {
                        if (!isLoopbackAddress && isIPv4(this)) {
                            Logger.debug("add: ${formatAddress(this)} ")

                            hostAddress?.let { ipv4 ->
                                networkMap[network] = ipv4
                                onIpChanged(ipv4)
                            }
                        }
                    }
                }
            }
        })
    }

    fun onIpChanged(ipv4: String) {
        if (localIP != ipv4) {
            localIP = ipv4
            App.instance.onNetworkChanged()
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
}
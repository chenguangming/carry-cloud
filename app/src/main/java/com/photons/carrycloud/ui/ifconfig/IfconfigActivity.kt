package com.photons.carrycloud.ui.ifconfig

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import com.photons.carrycloud.R
import com.photons.carrycloud.databinding.ActivityIfconfigBinding
import com.qmuiteam.qmui.arch.QMUIActivity
import com.qmuiteam.qmui.skin.QMUISkinManager


class IfconfigActivity : QMUIActivity() {
    private val TAG = "IfconfigActivity"
    private lateinit var binding: ActivityIfconfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityIfconfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 皮肤管理
        val skinManager = QMUISkinManager.defaultInstance(this)
        setSkinManager(skinManager)

        init()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return false
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * 获取所有网络的详细信息，并格式化为字符串。
     * 每个网络包括网络类型、网卡名称和所有 IP 地址，网卡之间以分隔符分隔。
     *
     * @param context 应用程序上下文
     * @return 格式化的网络信息字符串
     */
    fun getAllNetworksInfo(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val allNetworks = connectivityManager.allNetworks
        if (allNetworks.isEmpty()) {
            return "No networks available"
        }

        val result = StringBuilder()
        val separator = "\n---\n"

        for (network in allNetworks) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities == null) {
                Log.d(TAG, "No capabilities for network: $network")
                continue
            }

            // 获取网络类型
            val networkType = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Unknown"
            }

            // 获取网卡名称和 IP 地址
            val linkProperties = connectivityManager.getLinkProperties(network)
            val interfaceName = linkProperties?.interfaceName ?: "Unknown"
            val ipAddresses = mutableListOf<String>()
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                ipAddresses.add(linkAddress.address.hostAddress ?: "Unknown")
            }
            val ipAddressesStr = if (ipAddresses.isEmpty()) "None" else ipAddresses.joinToString("\n  ")

            // 格式化网络信息
            result.append("Network: $network\n")
            result.append("Type: $networkType\n")
            result.append("Interface: $interfaceName\n")
            result.append("IP Addresses: \n  $ipAddressesStr\n")
            result.append(separator)
        }

        // 移除末尾多余的分隔符
        if (result.isNotEmpty()) {
            result.setLength(result.length - separator.length)
        } else {
            result.append("No physical networks found")
        }

        return result.toString()
    }

    private fun init() {
        setSupportActionBar(binding.topbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.show_all_netcards)

        binding.networks.text = getAllNetworksInfo()
    }
}
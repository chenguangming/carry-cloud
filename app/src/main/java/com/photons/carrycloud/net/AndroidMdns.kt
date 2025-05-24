package com.photons.carrycloud.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.photons.carrycloud.App
import com.photons.carrycloud.Constants
import java.net.InetAddress

object AndroidMdns: BaseMdns(), NsdManager.DiscoveryListener, NsdManager.ResolveListener {
    private val TAG = "AndroidMdns"
    private val MDNS_SERVICE_TYPE = "_http._tcp"
    private val nsdManager = App.instance.getSystemService(Context.NSD_SERVICE) as NsdManager

    override fun start(address: String) {
        // 创建 NsdServiceInfo 对象
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = MDNS_SERVICE_NAME
            serviceType = MDNS_SERVICE_TYPE
            setPort(Constants.HTTP_PORT)
        }

        // 注册服务
        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "onServiceRegistered: $serviceInfo")
                    // 服务成功注册，PC 可通过 myservice.local:8818 访问
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.d(TAG, "onRegistrationFailed $serviceInfo with error code: $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "onServiceUnregistered: $serviceInfo")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.d(TAG, "Unregistration failed with error code: $errorCode")
                }
            }
        )
    }

    override fun stop() {
        nsdManager.unregisterService(object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "$serviceInfo unregistered successfully")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        })
    }

    // Called as soon as service discovery begins.
    override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
    }

    override fun onServiceFound(service: NsdServiceInfo) {
        // A service was found! Do something with it.
        Log.d(TAG, "Service discovery success$service")
        when {
            service.serviceType != MDNS_SERVICE_TYPE -> // Service type is the string containing the protocol and
                // transport layer for this service.
                Log.d(TAG, "Unknown Service Type: ${service.serviceType}")
            service.serviceName == MDNS_SERVICE_NAME -> // The name of the service tells the user what they'd be
                // connecting to. It could be "Bob's Chat App".
            {
                Log.d(TAG, "Same machine: $MDNS_SERVICE_NAME")
                try {
                    nsdManager.resolveService(service, this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.d(TAG, "service lost: $service")
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Log.d(TAG, "Discovery stopped: $serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.d(TAG, "Discovery failed: Error code:$errorCode")
        nsdManager.stopServiceDiscovery(this)
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.d(TAG, "Discovery failed: Error code:$errorCode")
        nsdManager.stopServiceDiscovery(this)
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        // Called when the resolve fails. Use the error code to debug.
        Log.d(TAG, "Resolve failed: $errorCode")
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        Log.d(TAG, "Resolve Succeeded. $serviceInfo")
        if (serviceInfo.serviceName.contains(MDNS_SERVICE_NAME)) {
            onDiscoveryMySelf(myAddress!!, serviceInfo.serviceName)
        }
    }
}
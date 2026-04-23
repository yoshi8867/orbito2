package com.yoshi0311.orbito.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.yoshi0311.orbito.model.RoomInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SERVICE_TYPE = "_orbito._tcp"
private const val TAG = "NsdHelper"

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolvedServices = mutableListOf<NsdServiceInfo>()
    private val resolving = mutableSetOf<String>()

    private val _rooms = MutableStateFlow<List<RoomInfo>>(emptyList())
    val rooms: StateFlow<List<RoomInfo>> = _rooms.asStateFlow()

    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null

    fun register(roomName: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = roomName
            serviceType = SERVICE_TYPE
            this.port = port
        }
        regListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, code: Int) { Log.e(TAG, "reg failed $code") }
            override fun onUnregistrationFailed(si: NsdServiceInfo, code: Int) {}
            override fun onServiceRegistered(si: NsdServiceInfo) { Log.d(TAG, "registered: ${si.serviceName}") }
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
        }
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener)
    }

    fun startDiscovery() {
        stopDiscovery()
        resolvedServices.clear()
        resolving.clear()
        _rooms.value = emptyList()

        discListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(st: String, c: Int) { Log.e(TAG, "disc start failed $c") }
            override fun onStopDiscoveryFailed(st: String, c: Int) {}
            override fun onDiscoveryStarted(st: String) { Log.d(TAG, "discovery started") }
            override fun onDiscoveryStopped(st: String) {}
            override fun onServiceFound(si: NsdServiceInfo) {
                if (si.serviceType.contains("_orbito") && resolving.add(si.serviceName)) {
                    nsdManager.resolveService(si, makeResolveListener())
                }
            }
            override fun onServiceLost(si: NsdServiceInfo) {
                resolvedServices.removeAll { it.serviceName == si.serviceName }
                resolving.remove(si.serviceName)
                publishRooms()
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
    }

    fun refresh() {
        startDiscovery()
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
            resolving.remove(si.serviceName)
            Log.e(TAG, "resolve failed $code for ${si.serviceName}")
        }
        override fun onServiceResolved(si: NsdServiceInfo) {
            resolving.remove(si.serviceName)
            resolvedServices.removeAll { it.serviceName == si.serviceName }
            resolvedServices.add(si)
            publishRooms()
        }
    }

    private fun publishRooms() {
        _rooms.value = resolvedServices.mapNotNull { si ->
            val host = si.host?.hostAddress ?: return@mapNotNull null
            RoomInfo(name = si.serviceName, host = host, port = si.port)
        }
    }

    fun stopDiscovery() {
        discListener?.let { try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {} }
        discListener = null
    }

    fun unregister() {
        regListener?.let { try { nsdManager.unregisterService(it) } catch (_: Exception) {} }
        regListener = null
    }

    fun stop() { stopDiscovery(); unregister() }
}

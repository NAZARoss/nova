package com.example.network.p2p

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

class P2PDiscovery(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var beaconJob: Job? = null
    private var listenerJob: Job? = null

    private val _discoveredAdminIp = MutableStateFlow<String?>(null)
    val discoveredAdminIp: StateFlow<String?> = _discoveredAdminIp.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("NovaP2PMulticast")?.apply {
                setReferenceCounted(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create multicast lock: ${e.message}")
        }
    }

    fun startAdminBeacon(port: Int = 8888, adminId: String = "ADMIN-PRIMARY") {
        stopAdminBeacon()
        beaconJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                while (isActive) {
                    val localIp = getLocalIpAddress() ?: "127.0.0.1"
                    val json = JSONObject().apply {
                        put("type", "NOVA_ADMIN_BEACON")
                        put("adminId", adminId)
                        put("ip", localIp)
                        put("port", port)
                        put("timestamp", System.currentTimeMillis())
                    }
                    val data = json.toString().toByteArray(Charsets.UTF_8)
                    
                    // Broadcast to standard subnet broadcast addresses
                    val targets = listOf(
                        InetAddress.getByName("255.255.255.255"),
                        InetAddress.getByName("127.0.0.1")
                    )
                    for (target in targets) {
                        try {
                            val packet = DatagramPacket(data, data.size, target, DISCOVERY_PORT)
                            socket.send(packet)
                        } catch (e: Exception) {
                            // Target might not be reachable
                        }
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Beacon error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun stopAdminBeacon() {
        beaconJob?.cancel()
        beaconJob = null
    }

    fun startClientDiscovery(onAdminFound: (String, Int) -> Unit) {
        stopClientDiscovery()
        try {
            multicastLock?.acquire()
        } catch (e: Exception) {
            // ignore
        }

        listenerJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    try {
                        val json = JSONObject(text)
                        if (json.optString("type") == "NOVA_ADMIN_BEACON") {
                            val ip = packet.address.hostAddress ?: json.optString("ip", "127.0.0.1")
                            val port = json.optInt("port", 8888)
                            _discoveredAdminIp.value = ip
                            onAdminFound(ip, port)
                        }
                    } catch (e: Exception) {
                        // Malformed packet
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery listener error: ${e.message}")
            } finally {
                socket?.close()
                try {
                    if (multicastLock?.isHeld == true) {
                        multicastLock?.release()
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun stopClientDiscovery() {
        listenerJob?.cancel()
        listenerJob = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    companion object {
        private const val TAG = "P2PDiscovery"
        const val DISCOVERY_PORT = 8889
    }
}

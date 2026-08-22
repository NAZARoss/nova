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
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

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
            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket()
                socket.reuseAddress = true
                socket.broadcast = true
                val multicastGroup = InetAddress.getByName(MULTICAST_GROUP)

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
                    
                    val packet = DatagramPacket(data, data.size, multicastGroup, DISCOVERY_PORT)
                    try {
                        socket.send(packet)
                        // Broadcast fallback
                        try {
                            val broadcastPacket = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                            socket.send(broadcastPacket)
                        } catch (e: Exception) { /* ignore */ }
                    } catch (e: Exception) {
                        Log.e(TAG, "Beacon send error: ${e.message}")
                    }
                    delay(1500)
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
            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket(DISCOVERY_PORT)
                socket.reuseAddress = true
                socket.soTimeout = 3000
                val multicastGroup = InetAddress.getByName(MULTICAST_GROUP)
                try {
                    socket.joinGroup(multicastGroup)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not join multicast group: ${e.message}")
                }
                
                val buffer = ByteArray(1024)
                var lastAdminIp: String? = null

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        if (json.optString("type") == "NOVA_ADMIN_BEACON") {
                            val ip = packet.address.hostAddress ?: json.optString("ip", "127.0.0.1")
                            val port = json.optInt("port", 8888)
                            if (ip != lastAdminIp) {
                                lastAdminIp = ip
                                _discoveredAdminIp.value = ip
                                onAdminFound(ip, port)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.w(TAG, "Discovery receive error: ${e.message}")
                        }
                        delay(500)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Discovery listener error: ${e.message}")
                }
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) { /* ignore */ }
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
                if (iface.name?.startsWith("docker") == true || iface.name?.startsWith("veth") == true) continue
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
        const val MULTICAST_GROUP = "239.255.255.250"
    }
}

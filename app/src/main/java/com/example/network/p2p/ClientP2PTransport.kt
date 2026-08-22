package com.example.network.p2p

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClientP2PTransport(
    private val context: Context,
    private val p2pDiscovery: P2PDiscovery
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var pollJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _connectionState = MutableStateFlow(P2PConnectionState.SEARCHING)
    val connectionState: StateFlow<P2PConnectionState> = _connectionState.asStateFlow()

    private val _adminHost = MutableStateFlow("127.0.0.1")
    val adminHost: StateFlow<String> = _adminHost.asStateFlow()

    private val _adminPort = MutableStateFlow(8888)
    val adminPort: StateFlow<Int> = _adminPort.asStateFlow()

    private val _incomingReplies = MutableSharedFlow<P2PMessagePayload>(extraBufferCapacity = 64)
    val incomingReplies: SharedFlow<P2PMessagePayload> = _incomingReplies.asSharedFlow()

    private var currentUserId: String = ""

    fun initialize(userId: String) {
        currentUserId = userId
        startDiscoveryAndListening()
    }

    fun setCustomAdminAddress(host: String, port: Int = 8888) {
        _adminHost.value = host
        _adminPort.value = port
        testConnection()
    }

    private fun startDiscoveryAndListening() {
        p2pDiscovery.startClientDiscovery { ip, port ->
            _adminHost.value = ip
            _adminPort.value = port
            _connectionState.value = P2PConnectionState.CONNECTED
        }

        startLongPollLoop()
    }

    private fun startLongPollLoop() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                val host = _adminHost.value
                val port = _adminPort.value
                val url = "http://$host:$port/api/poll?userId=$currentUserId"

                try {
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val bodyString = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        _connectionState.value = P2PConnectionState.CONNECTED
                        consecutiveFailures = 0

                        val json = JSONObject(bodyString)
                        val status = json.optString("status")
                        if (status == "ANSWER") {
                            val msgJson = json.getJSONObject("message")
                            val reply = P2PMessagePayload(
                                messageId = msgJson.getString("messageId"),
                                chatId = msgJson.getString("chatId"),
                                userId = msgJson.getString("userId"),
                                senderRole = msgJson.optString("senderRole", "AI_ADMIN"),
                                type = msgJson.optString("type", "ADMIN_REPLY"),
                                text = msgJson.getString("text"),
                                timestamp = msgJson.optLong("timestamp", System.currentTimeMillis())
                            )
                            _incomingReplies.emit(reply)
                        }
                    } else {
                        consecutiveFailures++
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures > 2) {
                        // If localhost fails, try searching again or toggle status
                        _connectionState.value = P2PConnectionState.OFFLINE
                    }
                    delay(2000)
                }

                // Brief yield before next poll cycle
                delay(300)
            }
        }
    }

    suspend fun sendMessage(payload: P2PMessagePayload): Boolean = withContext(Dispatchers.IO) {
        val host = _adminHost.value
        val port = _adminPort.value
        val url = "http://$host:$port/api/message"

        val json = JSONObject().apply {
            put("messageId", payload.messageId)
            put("chatId", payload.chatId)
            put("userId", payload.userId)
            put("senderRole", payload.senderRole)
            put("type", payload.type)
            put("text", payload.text)
            put("timestamp", payload.timestamp)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                _connectionState.value = P2PConnectionState.CONNECTED
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send message failed: ${e.message}")
            _connectionState.value = P2PConnectionState.OFFLINE
            false
        }
    }

    fun testConnection() {
        scope.launch {
            val host = _adminHost.value
            val port = _adminPort.value
            val url = "http://$host:$port/api/ping"
            try {
                val request = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    _connectionState.value = P2PConnectionState.CONNECTED
                } else {
                    _connectionState.value = P2PConnectionState.OFFLINE
                }
            } catch (e: Exception) {
                _connectionState.value = P2PConnectionState.OFFLINE
            }
        }
    }

    fun cleanup() {
        pollJob?.cancel()
        pollJob = null
        p2pDiscovery.stopClientDiscovery()
    }

    companion object {
        private const val TAG = "ClientP2PTransport"
    }
}

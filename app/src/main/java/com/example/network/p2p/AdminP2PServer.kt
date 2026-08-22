package com.example.network.p2p

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

class AdminP2PServer(val port: Int = 8888) {

    // Larger thread parallelism to prevent long-poll locks from starving POST requests
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(512))
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<P2PMessagePayload>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<P2PMessagePayload> = _incomingMessages.asSharedFlow()

    // Map of pending replies for each user: userId -> List of queued P2PMessagePayload
    private val pendingRepliesForUsers = ConcurrentHashMap<String, MutableList<P2PMessagePayload>>()
    // Long polling waiters: userId -> CompletableDeferred<P2PMessagePayload>
    private val activePollWaiters = ConcurrentHashMap<String, CompletableDeferred<P2PMessagePayload>>()

    // Connected clients tracking
    private val _connectedUserIps = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedUserIps: StateFlow<Map<String, String>> = _connectedUserIps.asStateFlow()

    fun start() {
        if (_isRunning.value) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                _isRunning.value = true
                Log.i(TAG, "P2P Admin Server started on port $port")

                while (isActive) {
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    clientSocket.soTimeout = 60000
                    launch {
                        handleClientConnection(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            } finally {
                _isRunning.value = false
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        serverJob = null

        activePollWaiters.values.forEach { it.cancel() }
        activePollWaiters.clear()
    }

    fun queueReplyForUser(userId: String, reply: P2PMessagePayload) {
        // If there's an active long-poll waiter, resolve it immediately!
        val waiter = activePollWaiters.remove(userId)
        if (waiter != null && waiter.isActive) {
            waiter.complete(reply)
            return
        }

        val queue = pendingRepliesForUsers.getOrPut(userId) { mutableListOf() }
        synchronized(queue) {
            queue.add(reply)
        }
    }

    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = try {
                reader.readLine()
            } catch (e: SocketTimeoutException) {
                return@withContext
            } ?: return@withContext

            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val path = parts[1]

            // Read HTTP headers
            var contentLength = 0
            var line: String? = null
            try {
                line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.lowercase().startsWith("content-length:")) {
                        contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                    }
                    line = reader.readLine()
                }
            } catch (e: SocketTimeoutException) {
                return@withContext
            }

            // Read Body
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    try {
                        val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                }
                bodyBuilder.append(bodyChars, 0, totalRead)
            }

            val body = bodyBuilder.toString()

            when {
                path.startsWith("/api/message") && method == "POST" -> {
                    handleMessagePost(body, clientIp, writer)
                }
                path.startsWith("/api/poll") && method == "GET" -> {
                    handlePollGet(path, clientIp, writer)
                }
                path.startsWith("/api/handshake") && method == "POST" -> {
                    handleHandshake(body, clientIp, writer)
                }
                path.startsWith("/api/ping") -> {
                    sendJsonResponse(writer, 200, JSONObject().apply {
                        put("status", "PONG")
                        put("admin", "NOVA_NODE")
                        put("time", System.currentTimeMillis())
                    }.toString())
                }
                else -> {
                    sendJsonResponse(writer, 404, "{\"error\":\"Not Found\"}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection handling error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private suspend fun handleMessagePost(body: String, clientIp: String, writer: PrintWriter) {
        try {
            val json = JSONObject(body)
            val payload = P2PMessagePayload(
                messageId = json.getString("messageId"),
                chatId = json.getString("chatId"),
                userId = json.getString("userId"),
                senderRole = json.optString("senderRole", "USER"),
                type = json.optString("type", "USER_MESSAGE"),
                text = json.getString("text"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )

            // Update connected clients mapping
            val currentMap = _connectedUserIps.value.toMutableMap()
            currentMap[payload.userId] = clientIp
            _connectedUserIps.value = currentMap

            // Emit to admin observers
            _incomingMessages.emit(payload)

            // Reply with Delivery ACK
            val response = JSONObject().apply {
                put("status", "DELIVERED")
                put("messageId", payload.messageId)
                put("timestamp", System.currentTimeMillis())
            }
            sendJsonResponse(writer, 200, response.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message payload: ${e.message}")
            sendJsonResponse(writer, 400, "{\"error\":\"Invalid Payload\"}")
        }
    }

    private suspend fun handlePollGet(path: String, clientIp: String, writer: PrintWriter) {
        // Extract userId from query string e.g. /api/poll?userId=USER-1234
        val queryParams = path.substringAfter("?", "")
        var userId = ""
        for (param in queryParams.split("&")) {
            val pair = param.split("=")
            if (pair.size == 2 && pair[0] == "userId") {
                userId = pair[1]
                break
            }
        }

        if (userId.isEmpty()) {
            sendJsonResponse(writer, 400, "{\"error\":\"Missing userId\"}")
            return
        }

        // Update IP mapping
        val currentMap = _connectedUserIps.value.toMutableMap()
        currentMap[userId] = clientIp
        _connectedUserIps.value = currentMap

        // Check if there are queued messages
        val queue = pendingRepliesForUsers[userId]
        var readyMessage: P2PMessagePayload? = null
        if (queue != null) {
            synchronized(queue) {
                if (queue.isNotEmpty()) {
                    readyMessage = queue.removeAt(0)
                }
            }
        }

        if (readyMessage != null) {
            sendReplyPayload(writer, readyMessage)
            return
        }

        // Otherwise, suspend with long-poll (up to 15 seconds)
        val waiter = CompletableDeferred<P2PMessagePayload>()
        activePollWaiters[userId] = waiter

        val polledReply = withTimeoutOrNull(15000L) {
            waiter.await()
        }
        activePollWaiters.remove(userId)

        if (polledReply != null) {
            sendReplyPayload(writer, polledReply)
        } else {
            // Long poll timeout -> send EMPTY response so client can poll again immediately
            sendJsonResponse(writer, 200, "{\"status\":\"EMPTY\"}")
        }
    }

    private fun handleHandshake(body: String, clientIp: String, writer: PrintWriter) {
        try {
            val json = JSONObject(body)
            val userId = json.optString("userId", "UNKNOWN")
            val currentMap = _connectedUserIps.value.toMutableMap()
            currentMap[userId] = clientIp
            _connectedUserIps.value = currentMap

            sendJsonResponse(writer, 200, JSONObject().apply {
                put("status", "CONNECTED")
                put("adminName", "Nova Primary Node")
                put("timestamp", System.currentTimeMillis())
            }.toString())
        } catch (e: Exception) {
            sendJsonResponse(writer, 200, "{\"status\":\"OK\"}")
        }
    }

    private fun sendReplyPayload(writer: PrintWriter, reply: P2PMessagePayload?) {
        if (reply == null) return
        val json = JSONObject().apply {
            put("status", "ANSWER")
            put("message", JSONObject().apply {
                put("messageId", reply.messageId)
                put("chatId", reply.chatId)
                put("userId", reply.userId)
                put("senderRole", reply.senderRole)
                put("type", reply.type)
                put("text", reply.text)
                put("timestamp", reply.timestamp)
            })
        }
        sendJsonResponse(writer, 200, json.toString())
    }

    private fun sendJsonResponse(writer: PrintWriter, statusCode: Int, json: String) {
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 404) "Not Found" else "Bad Request"
        val bytes = json.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(json)
        writer.flush()
    }

    companion object {
        private const val TAG = "AdminP2PServer"
    }
}

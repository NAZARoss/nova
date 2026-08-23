package com.example.network.colab

import android.content.Context
import android.content.SharedPreferences
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

enum class ColabConnectionStatus {
    NOT_CONFIGURED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ColabMessageItem(
    val id: Int,
    val userId: String,
    val sender: String,
    val text: String,
    val timestamp: Long
)

class ColabServerClient(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences("nova_colab_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, "") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _connectionStatus = MutableStateFlow(
        if (_serverUrl.value.isNotBlank()) ColabConnectionStatus.CONNECTING else ColabConnectionStatus.NOT_CONFIGURED
    )
    val connectionStatus: StateFlow<ColabConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingUserReplies = MutableSharedFlow<ColabMessageItem>(extraBufferCapacity = 64)
    val incomingUserReplies: SharedFlow<ColabMessageItem> = _incomingUserReplies.asSharedFlow()

    private val _incomingAdminMessages = MutableSharedFlow<ColabMessageItem>(extraBufferCapacity = 64)
    val incomingAdminMessages: SharedFlow<ColabMessageItem> = _incomingAdminMessages.asSharedFlow()

    private var userPollJob: Job? = null
    private var adminPollJob: Job? = null

    private var currentPollingUserId: String? = null

    init {
        if (_serverUrl.value.isNotBlank()) {
            checkConnection()
        }
    }

    fun isConfigured(): Boolean = _serverUrl.value.isNotBlank()

    fun cleanUrl(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed.trimEnd('/')
    }

    fun setServerUrl(url: String) {
        val cleaned = cleanUrl(url)
        _serverUrl.value = cleaned
        prefs.edit().putString(KEY_SERVER_URL, cleaned).apply()

        stopUserPolling()
        stopAdminPolling()

        if (cleaned.isBlank()) {
            _connectionStatus.value = ColabConnectionStatus.NOT_CONFIGURED
        } else {
            _connectionStatus.value = ColabConnectionStatus.CONNECTING
            checkConnection()
            // Restart polling with fresh state
            currentPollingUserId?.let { startUserPolling(it) }
            startAdminPolling()
        }
    }

    fun clearServerUrl() {
        setServerUrl("")
    }

    suspend fun testConnection(url: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val cleaned = cleanUrl(url)
        if (cleaned.isBlank()) {
            return@withContext Pair(false, "Server URL cannot be empty")
        }
        try {
            val endpoint = "$cleaned/api/messages?since=0"
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            if (response.isSuccessful && body.contains("\"status\"")) {
                Pair(true, null)
            } else {
                Pair(false, "Server responded with HTTP $code: ${body.take(100)}")
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection timed out or failed")
        }
    }

    private fun checkConnection() {
        scope.launch {
            val current = _serverUrl.value
            if (current.isBlank()) {
                _connectionStatus.value = ColabConnectionStatus.NOT_CONFIGURED
                return@launch
            }
            val (success, _) = testConnection(current)
            _connectionStatus.value = if (success) ColabConnectionStatus.CONNECTED else ColabConnectionStatus.ERROR
        }
    }

    suspend fun sendMessage(userId: String, sender: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val url = _serverUrl.value
        if (url.isBlank()) return@withContext false

        try {
            val json = JSONObject().apply {
                put("userId", userId)
                put("sender", sender)
                put("text", text)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$url/api/send")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val isSuccess = response.isSuccessful
            if (isSuccess) {
                _connectionStatus.value = ColabConnectionStatus.CONNECTED
            }
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Colab sendMessage error: ${e.message}")
            _connectionStatus.value = ColabConnectionStatus.ERROR
            false
        }
    }

    suspend fun clearMessagesOnServer(userId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val url = _serverUrl.value
        if (url.isBlank()) return@withContext false

        try {
            val endpoint = if (userId != null) "$url/api/clear?userId=$userId" else "$url/api/clear"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val emptyBody = "{}".toRequestBody(mediaType)
            val request = Request.Builder()
                .url(endpoint)
                .post(emptyBody)
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Colab clearMessages error: ${e.message}")
            false
        }
    }

    fun startUserPolling(userId: String) {
        currentPollingUserId = userId
        userPollJob?.cancel()
        userPollJob = scope.launch {
            var lastSeenSince = 0
            var consecutiveErrors = 0

            while (isActive) {
                val url = _serverUrl.value
                if (url.isBlank()) {
                    delay(2000)
                    continue
                }

                try {
                    val endpoint = "$url/api/messages?userId=$userId&since=$lastSeenSince"
                    val request = Request.Builder().url(endpoint).get().build()
                    val response = httpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        _connectionStatus.value = ColabConnectionStatus.CONNECTED
                        consecutiveErrors = 0
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val msgArray = json.optJSONArray("messages")
                        if (msgArray != null) {
                            val count = msgArray.length()
                            if (count > 0) {
                                for (i in 0 until count) {
                                    val obj = msgArray.getJSONObject(i)
                                    val item = ColabMessageItem(
                                        id = obj.optInt("id", lastSeenSince + i),
                                        userId = obj.optString("userId", userId),
                                        sender = obj.optString("sender", "USER"),
                                        text = obj.optString("text", ""),
                                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                                    )
                                    // If message from AI_ADMIN, emit to user replies
                                    if (item.sender == "AI_ADMIN" || item.sender == "ADMIN" || item.sender == "AI") {
                                        _incomingUserReplies.emit(item)
                                    }
                                }
                                lastSeenSince += count
                            }
                        }
                    } else {
                        consecutiveErrors++
                        if (consecutiveErrors > 3) {
                            _connectionStatus.value = ColabConnectionStatus.ERROR
                        }
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    if (consecutiveErrors > 3) {
                        _connectionStatus.value = ColabConnectionStatus.ERROR
                    }
                }

                delay(1000)
            }
        }
    }

    fun stopUserPolling() {
        userPollJob?.cancel()
        userPollJob = null
    }

    fun startAdminPolling() {
        adminPollJob?.cancel()
        adminPollJob = scope.launch {
            var lastSeenAdminSince = 0
            var consecutiveErrors = 0

            while (isActive) {
                val url = _serverUrl.value
                if (url.isBlank()) {
                    delay(2000)
                    continue
                }

                try {
                    val endpoint = "$url/api/messages?since=$lastSeenAdminSince"
                    val request = Request.Builder().url(endpoint).get().build()
                    val response = httpClient.newCall(request).execute()

                    if (response.isSuccessful) {
                        _connectionStatus.value = ColabConnectionStatus.CONNECTED
                        consecutiveErrors = 0
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val msgArray = json.optJSONArray("messages")
                        if (msgArray != null) {
                            val count = msgArray.length()
                            if (count > 0) {
                                for (i in 0 until count) {
                                    val obj = msgArray.getJSONObject(i)
                                    val item = ColabMessageItem(
                                        id = obj.optInt("id", lastSeenAdminSince + i),
                                        userId = obj.optString("userId", "anonymous"),
                                        sender = obj.optString("sender", "USER"),
                                        text = obj.optString("text", ""),
                                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                                    )
                                    if (item.sender == "USER") {
                                        _incomingAdminMessages.emit(item)
                                    }
                                }
                                lastSeenAdminSince += count
                            }
                        }
                    } else {
                        consecutiveErrors++
                        if (consecutiveErrors > 3) {
                            _connectionStatus.value = ColabConnectionStatus.ERROR
                        }
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    if (consecutiveErrors > 3) {
                        _connectionStatus.value = ColabConnectionStatus.ERROR
                    }
                }

                delay(1200)
            }
        }
    }

    fun stopAdminPolling() {
        adminPollJob?.cancel()
        adminPollJob = null
    }

    companion object {
        private const val TAG = "ColabServerClient"
        private const val KEY_SERVER_URL = "colab_tunnel_url"
    }
}

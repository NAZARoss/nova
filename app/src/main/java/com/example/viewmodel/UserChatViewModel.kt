package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Message
import com.example.data.repository.ChatRepository
import com.example.network.colab.ColabConnectionStatus
import com.example.network.p2p.P2PConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OnboardingStep {
    NONE,
    WARNING,
    AI_CAPABILITIES,
    TUTORIAL_ROLE,
    TUTORIAL_INPUT,
    TUTORIAL_SETTINGS,
    TUTORIAL_CLEAR
}

data class UserChatUiState(
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val connectionState: P2PConnectionState = P2PConnectionState.SEARCHING,
    val selectedTheme: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val showClearDialog: Boolean = false,
    val isTestingColab: Boolean = false,
    val colabTestMessage: String? = null,
    val showRoleEditDialog: Boolean = false,
    val roleInputText: String = "",
    val isBloodRedActive: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.NONE
)

class UserChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository.getInstance(application)

    val messages: StateFlow<List<Message>> = repository.getUserChatMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isWaitingForReply: StateFlow<Boolean> = repository.isUserWaitingForReply

    val connectionState: StateFlow<P2PConnectionState> = repository.clientTransport.connectionState

    val colabServerUrl: StateFlow<String> = repository.colabClient.serverUrl
    val colabConnectionStatus: StateFlow<ColabConnectionStatus> = repository.colabClient.connectionStatus

    val currentUserId: String = repository.currentUserId

    val aiRole: StateFlow<String> = repository.getUserAiRole()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Nova Assistant"
        )

    val prankEvents: SharedFlow<String> = repository.incomingPrankEvents

    private val _uiState = MutableStateFlow(UserChatUiState())
    val uiState: StateFlow<UserChatUiState> = _uiState.asStateFlow()

    init {
        if (!repository.isOnboardingCompleted()) {
            _uiState.value = _uiState.value.copy(onboardingStep = OnboardingStep.WARNING)
        }
    }

    fun advanceOnboarding() {
        val current = _uiState.value.onboardingStep
        val next = when (current) {
            OnboardingStep.WARNING -> OnboardingStep.AI_CAPABILITIES
            OnboardingStep.AI_CAPABILITIES -> OnboardingStep.TUTORIAL_ROLE
            OnboardingStep.TUTORIAL_ROLE -> OnboardingStep.TUTORIAL_INPUT
            OnboardingStep.TUTORIAL_INPUT -> OnboardingStep.TUTORIAL_SETTINGS
            OnboardingStep.TUTORIAL_SETTINGS -> OnboardingStep.TUTORIAL_CLEAR
            OnboardingStep.TUTORIAL_CLEAR -> {
                repository.setOnboardingCompleted(true)
                OnboardingStep.NONE
            }
            OnboardingStep.NONE -> OnboardingStep.NONE
        }
        _uiState.value = _uiState.value.copy(onboardingStep = next)
    }

    fun skipOnboarding() {
        repository.setOnboardingCompleted(true)
        _uiState.value = _uiState.value.copy(onboardingStep = OnboardingStep.NONE)
    }

    fun restartOnboarding() {
        repository.setOnboardingCompleted(false)
        _uiState.value = _uiState.value.copy(onboardingStep = OnboardingStep.WARNING)
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun openRoleEditDialog() {
        _uiState.value = _uiState.value.copy(
            showRoleEditDialog = true,
            roleInputText = aiRole.value
        )
    }

    fun dismissRoleEditDialog() {
        _uiState.value = _uiState.value.copy(showRoleEditDialog = false)
    }

    fun onRoleInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(roleInputText = text)
    }

    fun saveAiRole(role: String = _uiState.value.roleInputText) {
        val trimmed = role.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                repository.setUserAiRole(trimmed)
            }
        }
        _uiState.value = _uiState.value.copy(showRoleEditDialog = false)
    }

    fun setBloodRedMode(active: Boolean) {
        _uiState.value = _uiState.value.copy(isBloodRedActive = active)
    }

    fun sendMessage(text: String = _uiState.value.inputText) {
        if (isWaitingForReply.value) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _uiState.value = _uiState.value.copy(inputText = "")
        viewModelScope.launch {
            repository.sendUserMessage(trimmed)
        }
    }

    fun selectPromptChip(prompt: String) {
        _uiState.value = _uiState.value.copy(inputText = prompt)
    }

    fun openClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = true)
    }

    fun dismissClearDialog() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
    }

    fun confirmClearChat() {
        _uiState.value = _uiState.value.copy(showClearDialog = false)
        viewModelScope.launch {
            repository.clearUserChatHistory()
        }
    }

    fun setTheme(theme: String) {
        _uiState.value = _uiState.value.copy(selectedTheme = theme)
    }

    fun setColabServerUrl(url: String) {
        viewModelScope.launch {
            repository.updateColabServer(url, clearHistory = true)
        }
    }

    fun clearColabServerUrl() {
        viewModelScope.launch {
            repository.clearColabServer(clearHistory = true)
            _uiState.value = _uiState.value.copy(colabTestMessage = null)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    fun testColabConnection(url: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingColab = true, colabTestMessage = null)
            val (success, errorMsg) = repository.colabClient.testConnection(url)
            val message = if (success) "Connected successfully to Colab server!" else "Connection failed: $errorMsg"
            _uiState.value = _uiState.value.copy(
                isTestingColab = false,
                colabTestMessage = message
            )
            onResult(success, errorMsg)
        }
    }
}

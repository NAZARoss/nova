package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Message
import com.example.data.repository.ChatRepository
import com.example.network.p2p.P2PConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserChatUiState(
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val connectionState: P2PConnectionState = P2PConnectionState.SEARCHING,
    val selectedTheme: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val showClearDialog: Boolean = false
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

    val currentUserId: String = repository.currentUserId

    private val _uiState = MutableStateFlow(UserChatUiState())
    val uiState: StateFlow<UserChatUiState> = _uiState.asStateFlow()

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
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
}

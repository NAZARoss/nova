package com.example.data.model

enum class MessageSender {
    USER,
    AI_ADMIN,
    SYSTEM
}

enum class MessageType {
    USER_MESSAGE,
    ADMIN_REPLY,
    SYSTEM_MESSAGE
}

enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ,
    ANSWERED,
    FAILED
}

data class Message(
    val id: String,
    val chatId: String,
    val userId: String,
    val sender: MessageSender,
    val type: MessageType,
    val text: String,
    val timestamp: Long,
    val status: MessageStatus,
    val isDeliveredToAdmin: Boolean = false,
    val retryCount: Int = 0
)

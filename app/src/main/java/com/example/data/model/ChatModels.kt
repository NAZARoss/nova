package com.example.data.model

data class Chat(
    val id: String,
    val userId: String,
    val title: String,
    val lastMessage: String,
    val lastActivity: Long,
    val unreadCount: Int = 0,
    val isWaitingForReply: Boolean = false
)

package com.example.data.model

data class User(
    val id: String,
    val displayName: String,
    val createdAt: Long,
    val lastSeen: Long,
    val totalMessages: Int = 0,
    val ipAddress: String? = null,
    val isOnline: Boolean = false
)

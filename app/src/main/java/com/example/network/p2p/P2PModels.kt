package com.example.network.p2p

enum class P2PConnectionState {
    OFFLINE,
    SEARCHING,
    CONNECTING,
    CONNECTED
}

data class P2PMessagePayload(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val senderRole: String, // "USER" or "AI_ADMIN"
    val type: String, // "USER_MESSAGE", "ADMIN_REPLY", "SYSTEM_MESSAGE"
    val text: String,
    val timestamp: Long
)

data class P2PAckPayload(
    val messageId: String,
    val status: String, // "DELIVERED", "READ", "ANSWERED"
    val timestamp: Long
)

data class P2PAdminBeacon(
    val adminId: String,
    val hostIp: String,
    val port: Int,
    val timestamp: Long,
    val deviceName: String
)

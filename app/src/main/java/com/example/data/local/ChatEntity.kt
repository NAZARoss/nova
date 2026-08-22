package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Chat

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val title: String,
    val lastMessage: String,
    val lastActivity: Long,
    val unreadCount: Int = 0,
    val isWaitingForReply: Boolean = false
) {
    fun toDomain(): Chat {
        return Chat(
            id = id,
            userId = userId,
            title = title,
            lastMessage = lastMessage,
            lastActivity = lastActivity,
            unreadCount = unreadCount,
            isWaitingForReply = isWaitingForReply
        )
    }

    companion object {
        fun fromDomain(c: Chat): ChatEntity {
            return ChatEntity(
                id = c.id,
                userId = c.userId,
                title = c.title,
                lastMessage = c.lastMessage,
                lastActivity = c.lastActivity,
                unreadCount = c.unreadCount,
                isWaitingForReply = c.isWaitingForReply
            )
        }
    }
}

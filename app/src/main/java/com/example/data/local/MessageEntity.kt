package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["userId"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val userId: String,
    val sender: String,
    val type: String,
    val text: String,
    val timestamp: Long,
    val status: String,
    val isDeliveredToAdmin: Boolean = false,
    val retryCount: Int = 0
) {
    fun toDomain(): Message {
        return Message(
            id = id,
            chatId = chatId,
            userId = userId,
            sender = try { MessageSender.valueOf(sender) } catch (e: Exception) { MessageSender.USER },
            type = try { MessageType.valueOf(type) } catch (e: Exception) { MessageType.USER_MESSAGE },
            text = text,
            timestamp = timestamp,
            status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.SENT },
            isDeliveredToAdmin = isDeliveredToAdmin,
            retryCount = retryCount
        )
    }

    companion object {
        fun fromDomain(msg: Message): MessageEntity {
            return MessageEntity(
                id = msg.id,
                chatId = msg.chatId,
                userId = msg.userId,
                sender = msg.sender.name,
                type = msg.type.name,
                text = msg.text,
                timestamp = msg.timestamp,
                status = msg.status.name,
                isDeliveredToAdmin = msg.isDeliveredToAdmin,
                retryCount = msg.retryCount
            )
        }
    }
}

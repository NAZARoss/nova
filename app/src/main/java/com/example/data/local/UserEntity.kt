package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val createdAt: Long,
    val lastSeen: Long,
    val totalMessages: Int = 0,
    val ipAddress: String? = null,
    val isOnline: Boolean = false
) {
    fun toDomain(): User {
        return User(
            id = id,
            displayName = displayName,
            createdAt = createdAt,
            lastSeen = lastSeen,
            totalMessages = totalMessages,
            ipAddress = ipAddress,
            isOnline = isOnline
        )
    }

    companion object {
        fun fromDomain(u: User): UserEntity {
            return UserEntity(
                id = u.id,
                displayName = u.displayName,
                createdAt = u.createdAt,
                lastSeen = u.lastSeen,
                totalMessages = u.totalMessages,
                ipAddress = u.ipAddress,
                isOnline = u.isOnline
            )
        }
    }
}

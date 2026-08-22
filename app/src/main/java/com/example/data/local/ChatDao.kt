package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY lastActivity DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1")
    suspend fun getChatById(id: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE userId = :userId LIMIT 1")
    suspend fun getChatByUserId(userId: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatAsRead(chatId: String)

    @Query("UPDATE chats SET unreadCount = unreadCount + 1, lastMessage = :lastMsg, lastActivity = :timestamp, isWaitingForReply = 1 WHERE id = :chatId")
    suspend fun recordIncomingUserMessage(chatId: String, lastMsg: String, timestamp: Long)

    @Query("UPDATE chats SET lastMessage = :lastMsg, lastActivity = :timestamp, isWaitingForReply = 0 WHERE id = :chatId")
    suspend fun recordAdminReply(chatId: String, lastMsg: String, timestamp: Long)

    @Query("SELECT SUM(unreadCount) FROM chats")
    fun getTotalUnreadCount(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM chats WHERE isWaitingForReply = 1")
    fun getWaitingReplyCount(): Flow<Int>

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
}

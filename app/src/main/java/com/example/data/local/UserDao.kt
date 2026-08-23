package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY lastSeen DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserFlowById(id: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET isOnline = :isOnline, lastSeen = :timestamp WHERE id = :userId")
    suspend fun updateUserOnlineStatus(userId: String, isOnline: Boolean, timestamp: Long)

    @Query("UPDATE users SET selectedAiRole = :role, lastSeen = :timestamp WHERE id = :userId")
    suspend fun updateUserAiRole(userId: String, role: String, timestamp: Long)

    @Query("UPDATE users SET totalMessages = totalMessages + 1, lastSeen = :timestamp WHERE id = :userId")
    suspend fun incrementUserMessages(userId: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getTotalUserCount(): Int

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

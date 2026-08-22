package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.network.p2p.AdminP2PServer
import com.example.network.p2p.ClientP2PTransport
import com.example.network.p2p.P2PConnectionState
import com.example.network.p2p.P2PDiscovery
import com.example.network.p2p.P2PMessagePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val messageDao = database.messageDao()
    private val userDao = database.userDao()
    private val chatDao = database.chatDao()

    private val prefs: SharedPreferences = context.getSharedPreferences("nova_user_prefs", Context.MODE_PRIVATE)

    val p2pDiscovery = P2PDiscovery(context)
    val adminServer = AdminP2PServer(port = 8888)
    val clientTransport = ClientP2PTransport(context, p2pDiscovery)

    val currentUserId: String = getOrCreateUserId()
    val currentUserChatId: String = "CHAT-$currentUserId"

    private val _isUserWaitingForReply = MutableStateFlow(false)
    val isUserWaitingForReply: StateFlow<Boolean> = _isUserWaitingForReply.asStateFlow()

    private var retryJob: Job? = null

    init {
        // Initialize client node
        clientTransport.initialize(currentUserId)
        ensureLocalUserAndChat()
        listenForIncomingRepliesToUser()
        listenForIncomingMessagesToAdmin()
        startRetryWorker()
    }

    private fun getOrCreateUserId(): String {
        var id = prefs.getString("local_user_id", null)
        if (id.isNullOrEmpty()) {
            val randomSuffix = UUID.randomUUID().toString().substring(0, 8).uppercase()
            id = "USER-$randomSuffix"
            prefs.edit().putString("local_user_id", id).apply()
        }
        return id
    }

    private fun ensureLocalUserAndChat() {
        scope.launch {
            val existingUser = userDao.getUserById(currentUserId)
            if (existingUser == null) {
                userDao.upsertUser(
                    UserEntity(
                        id = currentUserId,
                        displayName = "User #${currentUserId.takeLast(4)}",
                        createdAt = System.currentTimeMillis(),
                        lastSeen = System.currentTimeMillis(),
                        totalMessages = 0
                    )
                )
            }

            val existingChat = chatDao.getChatById(currentUserChatId)
            if (existingChat == null) {
                chatDao.upsertChat(
                    ChatEntity(
                        id = currentUserChatId,
                        userId = currentUserId,
                        title = "Nova Assistant",
                        lastMessage = "",
                        lastActivity = System.currentTimeMillis(),
                        unreadCount = 0,
                        isWaitingForReply = false
                    )
                )
            } else {
                _isUserWaitingForReply.value = existingChat.isWaitingForReply
            }
        }
    }

    // ---------------------------------------------
    // USER CHAT OPERATIONS
    // ---------------------------------------------

    fun getUserChatMessages(): Flow<List<Message>> {
        return messageDao.getMessagesForChat(currentUserChatId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun sendUserMessage(text: String): Message {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val msg = Message(
            id = messageId,
            chatId = currentUserChatId,
            userId = currentUserId,
            sender = MessageSender.USER,
            type = MessageType.USER_MESSAGE,
            text = text,
            timestamp = timestamp,
            status = MessageStatus.PENDING,
            isDeliveredToAdmin = false
        )

        // 1. Save locally immediately
        messageDao.insertOrUpdate(MessageEntity.fromDomain(msg))
        chatDao.recordIncomingUserMessage(currentUserChatId, text, timestamp)
        _isUserWaitingForReply.value = true

        // 2. Transmit via P2P
        val payload = P2PMessagePayload(
            messageId = messageId,
            chatId = currentUserChatId,
            userId = currentUserId,
            senderRole = MessageSender.USER.name,
            type = MessageType.USER_MESSAGE.name,
            text = text,
            timestamp = timestamp
        )

        scope.launch {
            val delivered = clientTransport.sendMessage(payload)
            if (delivered) {
                messageDao.updateDeliveryStatus(messageId, MessageStatus.DELIVERED.name, true)
            } else {
                messageDao.updateStatus(messageId, MessageStatus.PENDING.name)
            }
        }

        return msg
    }

    private fun listenForIncomingRepliesToUser() {
        scope.launch {
            clientTransport.incomingReplies.collect { replyPayload ->
                if (replyPayload.userId == currentUserId) {
                    val replyMessage = Message(
                        id = replyPayload.messageId,
                        chatId = currentUserChatId,
                        userId = currentUserId,
                        sender = MessageSender.AI_ADMIN,
                        type = MessageType.ADMIN_REPLY,
                        text = replyPayload.text,
                        timestamp = replyPayload.timestamp,
                        status = MessageStatus.ANSWERED,
                        isDeliveredToAdmin = true
                    )

                    messageDao.insertOrUpdate(MessageEntity.fromDomain(replyMessage))
                    chatDao.recordAdminReply(currentUserChatId, replyPayload.text, replyPayload.timestamp)
                    _isUserWaitingForReply.value = false

                    // Play gentle sound on client response
                    playNotificationFeedback(isSoundOnly = true)
                }
            }
        }
    }

    suspend fun clearUserChatHistory() {
        messageDao.deleteMessagesForChat(currentUserChatId)
        chatDao.upsertChat(
            ChatEntity(
                id = currentUserChatId,
                userId = currentUserId,
                title = "Nova Assistant",
                lastMessage = "",
                lastActivity = System.currentTimeMillis(),
                unreadCount = 0,
                isWaitingForReply = false
            )
        )
        _isUserWaitingForReply.value = false
    }

    private fun startRetryWorker() {
        retryJob?.cancel()
        retryJob = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    val undelivered = messageDao.getUndeliveredMessages()
                    for (entity in undelivered) {
                        if (entity.sender == MessageSender.USER.name) {
                            val payload = P2PMessagePayload(
                                messageId = entity.id,
                                chatId = entity.chatId,
                                userId = entity.userId,
                                senderRole = entity.sender,
                                type = entity.type,
                                text = entity.text,
                                timestamp = entity.timestamp
                            )
                            val sent = clientTransport.sendMessage(payload)
                            if (sent) {
                                messageDao.updateDeliveryStatus(entity.id, MessageStatus.DELIVERED.name, true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Retry worker error: ${e.message}")
                }
            }
        }
    }

    // ---------------------------------------------
    // ADMIN OPERATIONS
    // ---------------------------------------------

    fun startAdminNode() {
        adminServer.start()
        p2pDiscovery.startAdminBeacon(port = 8888, adminId = "ADMIN-PRIMARY")
    }

    fun stopAdminNode() {
        p2pDiscovery.stopAdminBeacon()
        adminServer.stop()
    }

    fun getAllAdminChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { list -> list.map { it.toDomain() } }
    }

    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { list -> list.map { it.toDomain() } }
    }

    fun getMessagesForUser(userId: String): Flow<List<Message>> {
        return messageDao.getMessagesForUser(userId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getTotalUnreadCount(): Flow<Int?> = chatDao.getTotalUnreadCount()
    fun getWaitingReplyCount(): Flow<Int> = chatDao.getWaitingReplyCount()

    suspend fun sendAdminReply(targetUserId: String, text: String): Message {
        val targetChatId = "CHAT-$targetUserId"
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val reply = Message(
            id = messageId,
            chatId = targetChatId,
            userId = targetUserId,
            sender = MessageSender.AI_ADMIN,
            type = MessageType.ADMIN_REPLY,
            text = text,
            timestamp = timestamp,
            status = MessageStatus.ANSWERED,
            isDeliveredToAdmin = true
        )

        // 1. Save in admin's local Room database
        messageDao.insertOrUpdate(MessageEntity.fromDomain(reply))
        chatDao.recordAdminReply(targetChatId, text, timestamp)

        // 2. Queue and push to user via P2P server
        val payload = P2PMessagePayload(
            messageId = messageId,
            chatId = targetChatId,
            userId = targetUserId,
            senderRole = MessageSender.AI_ADMIN.name,
            type = MessageType.ADMIN_REPLY.name,
            text = text,
            timestamp = timestamp
        )
        adminServer.queueReplyForUser(targetUserId, payload)

        return reply
    }

    suspend fun markChatAsRead(chatId: String) {
        chatDao.markChatAsRead(chatId)
    }

    private fun listenForIncomingMessagesToAdmin() {
        scope.launch {
            adminServer.incomingMessages.collect { incoming ->
                val timestamp = incoming.timestamp
                val userId = incoming.userId
                val chatId = incoming.chatId

                // 1. Ensure user exists
                val existingUser = userDao.getUserById(userId)
                if (existingUser == null) {
                    userDao.upsertUser(
                        UserEntity(
                            id = userId,
                            displayName = "User #${userId.takeLast(4)}",
                            createdAt = timestamp,
                            lastSeen = timestamp,
                            totalMessages = 1,
                            isOnline = true
                        )
                    )
                } else {
                    userDao.incrementUserMessages(userId, timestamp)
                    userDao.updateUserOnlineStatus(userId, true, timestamp)
                }

                // 2. Insert message into Room (deduplicated by PrimaryKey messageId)
                val msgEntity = MessageEntity(
                    id = incoming.messageId,
                    chatId = chatId,
                    userId = userId,
                    sender = incoming.senderRole,
                    type = incoming.type,
                    text = incoming.text,
                    timestamp = timestamp,
                    status = MessageStatus.DELIVERED.name,
                    isDeliveredToAdmin = true
                )
                messageDao.insertOrUpdate(msgEntity)

                // 3. Update Chat row
                val existingChat = chatDao.getChatById(chatId)
                if (existingChat == null) {
                    chatDao.upsertChat(
                        ChatEntity(
                            id = chatId,
                            userId = userId,
                            title = "User #${userId.takeLast(4)}",
                            lastMessage = incoming.text,
                            lastActivity = timestamp,
                            unreadCount = 1,
                            isWaitingForReply = true
                        )
                    )
                } else {
                    chatDao.recordIncomingUserMessage(chatId, incoming.text, timestamp)
                }

                // 4. Admin alert notification
                playNotificationFeedback(isSoundOnly = false)
            }
        }
    }

    private fun playNotificationFeedback(isSoundOnly: Boolean) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context.applicationContext, notificationUri)
            r.play()

            if (!isSoundOnly) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(150)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ChatRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

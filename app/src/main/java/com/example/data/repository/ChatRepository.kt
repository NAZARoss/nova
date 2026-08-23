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
import com.example.data.model.PrankCommands
import com.example.data.model.User
import com.example.network.colab.ColabServerClient
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
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
    val colabClient = ColabServerClient(context)

    val currentUserId: String = getOrCreateUserId()
    val currentUserChatId: String = "CHAT-$currentUserId"

    private val _isUserWaitingForReply = MutableStateFlow(false)
    val isUserWaitingForReply: StateFlow<Boolean> = _isUserWaitingForReply.asStateFlow()

    private val _incomingPrankEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingPrankEvents: SharedFlow<String> = _incomingPrankEvents.asSharedFlow()

    private val seenMessageKeys = Collections.synchronizedSet(mutableSetOf<String>())

    private var retryJob: Job? = null

    init {
        // Initialize client node & Colab polling
        clientTransport.initialize(currentUserId)
        colabClient.startUserPolling(currentUserId)
        colabClient.startAdminPolling()

        ensureLocalUserAndChat()
        listenForIncomingRepliesToUser()
        listenForIncomingMessagesToAdmin()
        listenForColabRepliesToUser()
        listenForColabIncomingMessagesToAdmin()
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

        // 2. Transmit via Colab Server or P2P
        scope.launch {
            val delivered = if (colabClient.isConfigured()) {
                colabClient.sendMessage(currentUserId, MessageSender.USER.name, text)
            } else {
                val payload = P2PMessagePayload(
                    messageId = messageId,
                    chatId = currentUserChatId,
                    userId = currentUserId,
                    senderRole = MessageSender.USER.name,
                    type = MessageType.USER_MESSAGE.name,
                    text = text,
                    timestamp = timestamp
                )
                clientTransport.sendMessage(payload)
            }

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
                    handleIncomingUserReply(
                        id = replyPayload.messageId,
                        text = replyPayload.text,
                        timestamp = replyPayload.timestamp
                    )
                }
            }
        }
    }

    private fun listenForColabRepliesToUser() {
        scope.launch {
            colabClient.incomingUserReplies.collect { item ->
                if (item.userId == currentUserId) {
                    handleIncomingUserReply(
                        id = "COLAB-${item.id}-${item.timestamp}",
                        text = item.text,
                        timestamp = item.timestamp
                    )
                }
            }
        }
    }

    private suspend fun handleIncomingUserReply(id: String, text: String, timestamp: Long) {
        // Intercept prank commands
        if (PrankCommands.isPrankCommand(text)) {
            val prankType = PrankCommands.extractType(text)
            if (prankType != null) {
                _incomingPrankEvents.emit(prankType)
            }
            return
        }

        // Intercept AI Role sync
        if (text.startsWith(":::AI_ROLE:::")) {
            val newRole = text.substringAfter(":::AI_ROLE:::").trim()
            userDao.updateUserAiRole(currentUserId, newRole, timestamp)
            return
        }

        val dedupKey = "$currentUserId-REPLY-$text-${timestamp / 1500}"
        if (!seenMessageKeys.add(dedupKey)) {
            Log.d("ChatRepository", "Ignored duplicated reply from admin")
            _isUserWaitingForReply.value = false
            return
        }

        val replyMessage = Message(
            id = id,
            chatId = currentUserChatId,
            userId = currentUserId,
            sender = MessageSender.AI_ADMIN,
            type = MessageType.ADMIN_REPLY,
            text = text,
            timestamp = timestamp,
            status = MessageStatus.ANSWERED,
            isDeliveredToAdmin = true
        )

        messageDao.insertOrUpdate(MessageEntity.fromDomain(replyMessage))
        chatDao.recordAdminReply(currentUserChatId, text, timestamp)
        _isUserWaitingForReply.value = false

        // Play gentle sound on client response
        playNotificationFeedback(isSoundOnly = true)
    }

    suspend fun setUserAiRole(role: String) {
        val trimmed = role.trim()
        if (trimmed.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        userDao.updateUserAiRole(currentUserId, trimmed, timestamp)

        // Broadcast AI role update to admin via Colab or P2P
        val rolePayloadText = ":::AI_ROLE:::$trimmed"
        scope.launch {
            if (colabClient.isConfigured()) {
                colabClient.sendMessage(currentUserId, MessageSender.USER.name, rolePayloadText)
            } else {
                val payload = P2PMessagePayload(
                    messageId = UUID.randomUUID().toString(),
                    chatId = currentUserChatId,
                    userId = currentUserId,
                    senderRole = MessageSender.USER.name,
                    type = "AI_ROLE_UPDATE",
                    text = rolePayloadText,
                    timestamp = timestamp
                )
                clientTransport.sendMessage(payload)
            }
        }
    }

    fun getUserAiRole(): Flow<String> {
        return userDao.getUserFlowById(currentUserId).map { it?.selectedAiRole ?: "Nova Assistant" }
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

        if (colabClient.isConfigured()) {
            colabClient.clearMessagesOnServer(currentUserId)
        }
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
                            val sent = if (colabClient.isConfigured()) {
                                colabClient.sendMessage(entity.userId, MessageSender.USER.name, entity.text)
                            } else {
                                val payload = P2PMessagePayload(
                                    messageId = entity.id,
                                    chatId = entity.chatId,
                                    userId = entity.userId,
                                    senderRole = entity.sender,
                                    type = entity.type,
                                    text = entity.text,
                                    timestamp = entity.timestamp
                                )
                                clientTransport.sendMessage(payload)
                            }
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

        // 2. Transmit via Colab Server if configured
        if (colabClient.isConfigured()) {
            scope.launch {
                colabClient.sendMessage(targetUserId, MessageSender.AI_ADMIN.name, text)
            }
        }

        // 3. Queue and push to user via P2P server
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

    suspend fun sendAdminPrank(targetUserId: String, prankType: String) {
        val prankCommand = PrankCommands.buildCommand(prankType)
        val targetChatId = "CHAT-$targetUserId"
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Send via Colab if configured
        if (colabClient.isConfigured()) {
            scope.launch {
                colabClient.sendMessage(targetUserId, MessageSender.AI_ADMIN.name, prankCommand)
            }
        }

        // Send via P2P
        val payload = P2PMessagePayload(
            messageId = messageId,
            chatId = targetChatId,
            userId = targetUserId,
            senderRole = MessageSender.AI_ADMIN.name,
            type = "SYSTEM_PRANK",
            text = prankCommand,
            timestamp = timestamp
        )
        adminServer.queueReplyForUser(targetUserId, payload)
    }

    suspend fun markChatAsRead(chatId: String) {
        chatDao.markChatAsRead(chatId)
    }

    private fun listenForIncomingMessagesToAdmin() {
        scope.launch {
            adminServer.incomingMessages.collect { incoming ->
                handleIncomingMessageToAdmin(
                    messageId = incoming.messageId,
                    chatId = incoming.chatId,
                    userId = incoming.userId,
                    senderRole = incoming.senderRole,
                    type = incoming.type,
                    text = incoming.text,
                    timestamp = incoming.timestamp
                )
            }
        }
    }

    private fun listenForColabIncomingMessagesToAdmin() {
        scope.launch {
            colabClient.incomingAdminMessages.collect { item ->
                handleIncomingMessageToAdmin(
                    messageId = "COLAB-${item.id}-${item.timestamp}",
                    chatId = "CHAT-${item.userId}",
                    userId = item.userId,
                    senderRole = item.sender,
                    type = MessageType.USER_MESSAGE.name,
                    text = item.text,
                    timestamp = item.timestamp
                )
            }
        }
    }

    private suspend fun handleIncomingMessageToAdmin(
        messageId: String,
        chatId: String,
        userId: String,
        senderRole: String,
        type: String,
        text: String,
        timestamp: Long
    ) {
        // Intercept AI Role sync message
        if (text.startsWith(":::AI_ROLE:::")) {
            val role = text.substringAfter(":::AI_ROLE:::").trim()
            val existing = userDao.getUserById(userId)
            if (existing == null) {
                userDao.upsertUser(
                    UserEntity(
                        id = userId,
                        displayName = "User #${userId.takeLast(4)}",
                        createdAt = timestamp,
                        lastSeen = timestamp,
                        totalMessages = 0,
                        isOnline = true,
                        selectedAiRole = role
                    )
                )
            } else {
                userDao.updateUserAiRole(userId, role, timestamp)
            }
            return
        }

        // Ignore admin commands or pranks if echoed
        if (PrankCommands.isPrankCommand(text)) {
            return
        }

        // Deduplicate user messages arriving via multiple paths
        val dedupKey = "$userId-USER-$text-${timestamp / 1500}"
        if (!seenMessageKeys.add(dedupKey)) {
            Log.d("ChatRepository", "Ignored duplicated incoming message from $userId")
            return
        }

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
            id = messageId,
            chatId = chatId,
            userId = userId,
            sender = senderRole,
            type = type,
            text = text,
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
                    lastMessage = text,
                    lastActivity = timestamp,
                    unreadCount = 1,
                    isWaitingForReply = true
                )
            )
        } else {
            chatDao.recordIncomingUserMessage(chatId, text, timestamp)
        }

        // 4. Admin alert notification
        playNotificationFeedback(isSoundOnly = false)
    }

    private fun playNotificationFeedback(isSoundOnly: Boolean) {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context.applicationContext, notificationUri)
            r.play()

            if (!isSoundOnly) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
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

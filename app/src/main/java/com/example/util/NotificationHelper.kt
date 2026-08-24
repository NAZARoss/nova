package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_MESSAGES_ID = "nova_chat_messages_channel"
    const val CHANNEL_MESSAGES_NAME = "Chat Messages & Replies"

    const val CHANNEL_SERVICE_ID = "nova_sync_service_channel"
    const val CHANNEL_SERVICE_NAME = "Background Connection Service"

    const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
    const val EXTRA_USER_ID = "extra_user_id"
    const val NAV_USER_CHAT = "user_chat"
    const val NAV_ADMIN_CHAT = "admin_chat"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. High Priority Messages Channel
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new AI replies and user messages"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(messagesChannel)

            // 2. Low Priority Background Sync Channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                CHANNEL_SERVICE_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps server connection active in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun showReplyToUserNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatId: String
    ) {
        try {
            createNotificationChannels(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAVIGATE_TO, NAV_USER_CHAT)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 150, 100, 150))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(2001, notification)
            Log.d(TAG, "User notification posted: $senderName -> $messageText")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show user notification: ${e.message}", e)
        }
    }

    fun showIncomingMessageToAdminNotification(
        context: Context,
        userDisplayName: String,
        userId: String,
        messageText: String
    ) {
        try {
            createNotificationChannels(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAVIGATE_TO, NAV_ADMIN_CHAT)
                putExtra(EXTRA_USER_ID, userId)
            }

            val notifId = (userId.hashCode() and 0x7FFFFFFF) + 5000

            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(userDisplayName)
                .setContentText(messageText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 150, 100, 150))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notifId, notification)
            Log.d(TAG, "Admin notification posted: $userDisplayName -> $messageText")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show admin notification: ${e.message}", e)
        }
    }
}

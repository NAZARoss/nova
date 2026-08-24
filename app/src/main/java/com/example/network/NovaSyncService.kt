package com.example.network

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.repository.ChatRepository
import com.example.util.NotificationHelper

class NovaSyncService : Service() {

    private val TAG = "NovaSyncService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NovaSyncService created, initializing ChatRepository for persistent polling")
        NotificationHelper.createNotificationChannels(this)
        // Ensure the singleton repository and its polling coroutines are running
        ChatRepository.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NovaSyncService onStartCommand")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SERVICE_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Nova AI Connection")
                    .setContentText("Listening for messages and updates")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                    if (serviceType != 0) {
                        startForeground(SYNC_SERVICE_NOTIFICATION_ID, notification, serviceType)
                    } else {
                        startForeground(SYNC_SERVICE_NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(SYNC_SERVICE_NOTIFICATION_ID, notification)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed (running as background service): ${e.message}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SYNC_SERVICE_NOTIFICATION_ID = 9001

        fun start(context: Context) {
            try {
                val intent = Intent(context, NovaSyncService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("NovaSyncService", "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, NovaSyncService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e("NovaSyncService", "Failed to stop service: ${e.message}")
            }
        }
    }
}

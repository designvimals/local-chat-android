package com.example.privatevault.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.privatevault.MainActivity
import com.example.privatevault.R
import com.example.privatevault.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StorageSharingForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = when (intent?.action) {
            StorageNotificationActions.ACTION_PAUSE -> {
                serviceScope.launch {
                    SettingsStore(applicationContext).setStorageSharingEnabled(false)
                }
                SharingMode.Paused
            }
            else -> if (intent?.getBooleanExtra(StorageNotificationActions.EXTRA_STORAGE_ENABLED, true) == true) {
                SharingMode.Available
            } else {
                SharingMode.Paused
            }
        }

        startForeground(StorageNotificationActions.NOTIFICATION_ID, buildNotification(mode))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            StorageNotificationActions.CHANNEL_ID,
            "Private connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps private chat and approved file access available."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(mode: SharingMode): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StorageSharingForegroundService::class.java)
                .setAction(StorageNotificationActions.ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = when (mode) {
            SharingMode.Available -> "Private chat and files are online" to "Only your paired device can connect."
            SharingMode.Paused -> "Private chat is online" to "File access is paused. Open the app to resume it."
        }

        return NotificationCompat.Builder(this, StorageNotificationActions.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_folder_24)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (mode != SharingMode.Paused) {
                    addAction(R.drawable.ic_pause_24, "Pause files", pauseIntent)
                }
            }
            .build()
    }

    private enum class SharingMode { Available, Paused }
}

class StorageSessionNotifier(private val context: Context) {
    fun markAvailable(storageSharingEnabled: Boolean) {
        start(
            Intent(context, StorageSharingForegroundService::class.java)
                .setAction(StorageNotificationActions.ACTION_AVAILABLE)
                .putExtra(StorageNotificationActions.EXTRA_STORAGE_ENABLED, storageSharingEnabled)
        )
    }

    fun markInactive() {
        context.stopService(Intent(context, StorageSharingForegroundService::class.java))
    }

    private fun start(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

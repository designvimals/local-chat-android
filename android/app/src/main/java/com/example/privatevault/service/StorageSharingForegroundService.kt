package com.example.privatevault.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.privatevault.MainActivity
import com.example.privatevault.R
import com.example.privatevault.app.AppRuntime
import com.example.privatevault.app.PrivateVaultApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Owns every connection that must outlive the activity. Leaving the UI or
 * removing the app task from Recents keeps this foreground service running.
 */
class StorageSharingForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var runtime: AppRuntime
    private var relayJob: Job? = null
    private var peerRelayJob: Job? = null
    private var messageNotificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        runtime = (application as PrivateVaultApplication).runtime
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == StorageNotificationActions.ACTION_PAUSE) {
            serviceScope.launch {
                runtime.settingsStore.setStorageSharingEnabled(false)
                runtime.backendClient.updateStorageSharing(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        val storageAvailable = intent?.getBooleanExtra(
            StorageNotificationActions.EXTRA_STORAGE_ENABLED,
            false
        ) == true
        if (!storageAvailable) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        promoteToForeground()

        serviceScope.launch {
            runtime.localServerManager.start(runtime.tokenStore.getAccessToken())
            runtime.backendClient.updateStorageSharing(true)
            ensureConnectionLoops()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        relayJob?.cancel()
        peerRelayJob?.cancel()
        messageNotificationJob?.cancel()
        runBlocking(Dispatchers.IO) {
            runCatching { runtime.backendClient.restartConnection() }
            runCatching { runtime.peerRelayClient.restartConnection() }
        }
        runtime.localServerManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureConnectionLoops() {
        if (messageNotificationJob?.isActive != true) {
            val policy = IncomingMessageNotificationPolicy(
                currentDeviceId = runtime.deviceRepository.deviceId,
                initialMessages = runtime.chatRepository.messages.value
            )
            messageNotificationJob = serviceScope.launch {
                runtime.chatRepository.messages.collect { messages ->
                    if (policy.onMessagesChanged(messages, runtime.chatVisibilityTracker.isChatVisible)) {
                        runtime.incomingMessageNotifier.show()
                    }
                }
            }
        }
        if (relayJob?.isActive != true) {
            relayJob = serviceScope.launch {
                while (isActive) {
                    val storageAvailable = runtime.settingsStore.storageSharingEnabled.first() &&
                        runtime.settingsStore.storagePermissionGranted.first()
                    runtime.backendClient.connectRelay(storageSharingEnabled = storageAvailable)
                    delay(2_000)
                }
            }
        }
        if (peerRelayJob?.isActive != true) {
            peerRelayJob = serviceScope.launch {
                while (isActive) {
                    val connection = runtime.tokenStore.getPeerConnection()
                    if (connection == null) {
                        delay(2_000)
                        continue
                    }
                    runtime.peerRelayClient.connectPeer(connection)
                    delay(2_000)
                }
            }
        }
    }

    // The debug/sandbox manifest intentionally removes this production-only service.
    @SuppressLint("ForegroundServiceType")
    private fun promoteToForeground() {
        ServiceCompat.startForeground(
            this,
            StorageNotificationActions.NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            StorageNotificationActions.CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
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
        return NotificationCompat.Builder(this, StorageNotificationActions.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_folder_24)
            .setContentTitle(getString(R.string.notification_available_title))
            .setContentText(getString(R.string.notification_available_body))
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_pause_24, getString(R.string.notification_pause_action), pauseIntent)
            .build()
    }
}

class StorageSessionNotifier(private val context: Context) {
    fun markAvailable(storageSharingEnabled: Boolean) {
        if (!storageSharingEnabled) {
            markInactive()
            return
        }
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

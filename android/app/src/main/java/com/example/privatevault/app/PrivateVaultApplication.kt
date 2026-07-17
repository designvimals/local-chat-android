package com.example.privatevault.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.privatevault.backup.ChatBackupScheduler

class PrivateVaultApplication : Application() {
    lateinit var runtime: AppRuntime
        private set

    var resetInProgress: Boolean = false
        private set

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                runtime.appLockManager.lockForDeviceScreenOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        resetInProgress = OneTimeFullReset.requestIfNeeded(this)
        if (resetInProgress) return

        runtime = AppRuntime(applicationContext)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_EXPORTED
        )
        ChatBackupScheduler.scheduleNext(this)
    }
}

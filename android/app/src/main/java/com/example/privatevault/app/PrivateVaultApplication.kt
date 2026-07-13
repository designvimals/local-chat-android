package com.example.privatevault.app

import android.app.Application
import com.example.privatevault.backup.ChatBackupScheduler

class PrivateVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ChatBackupScheduler.scheduleNext(this)
    }
}

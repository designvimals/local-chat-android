package com.example.privatevault.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { ChatBackupManager(applicationContext).createBackup("daily") }
            .fold(
                onSuccess = {
                    ChatBackupScheduler.scheduleNext(applicationContext)
                    Result.success()
                },
                onFailure = { Result.retry() }
            )
    }
}

object ChatBackupScheduler {
    private const val UNIQUE_WORK_NAME = "daily-text-chat-backup-at-9pm"

    fun scheduleNext(context: Context) {
        val now = ZonedDateTime.now()
        var next = now.withHour(21).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<ChatBackupWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

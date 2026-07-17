package com.example.privatevault.app

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import com.example.privatevault.BuildConfig
import java.io.File

internal enum class FullResetAction {
    None,
    MarkComplete,
    RequestClear
}

internal fun decideFullResetAction(
    versionCode: Int,
    resetCompleted: Boolean,
    legacyAppStateExists: Boolean
): FullResetAction = when {
    versionCode != OneTimeFullReset.RESET_VERSION_CODE -> FullResetAction.None
    resetCompleted -> FullResetAction.None
    legacyAppStateExists -> FullResetAction.RequestClear
    else -> FullResetAction.MarkComplete
}

/**
 * Requests Android's full Clear storage operation exactly once for the v0.3.6
 * migration. The completion marker is written only on the first clean launch,
 * after Android has removed the previous app data.
 */
object OneTimeFullReset {
    internal const val RESET_VERSION_CODE = 12
    private const val PREFERENCES_NAME = "one_time_full_reset"
    private const val KEY_COMPLETED = "completed_v12"

    fun requestIfNeeded(context: Context): Boolean {
        if (BuildConfig.LOCAL_ONLY) return false
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return when (
            decideFullResetAction(
                versionCode = BuildConfig.VERSION_CODE,
                resetCompleted = preferences.getBoolean(KEY_COMPLETED, false),
                legacyAppStateExists = legacyAppStateExists(context)
            )
        ) {
            FullResetAction.None -> false
            FullResetAction.MarkComplete -> {
                check(preferences.edit().putBoolean(KEY_COMPLETED, true).commit()) {
                    "The one-time reset completion marker could not be saved."
                }
                false
            }
            FullResetAction.RequestClear -> {
                check(context.getSystemService(ActivityManager::class.java).clearApplicationUserData()) {
                    "Android rejected the one-time Clear storage request."
                }
                true
            }
        }
    }

    private fun legacyAppStateExists(context: Context): Boolean {
        val sharedPreferences = File(context.applicationInfo.dataDir, "shared_prefs")
        val knownPreferenceFiles = listOf(
            File(sharedPreferences, "tokens.xml"),
            File(sharedPreferences, "chat_attachments.xml"),
            File(sharedPreferences, "app_lock.xml")
        )
        val knownPrivateFiles = listOf(
            File(context.filesDir, "messages.txt"),
            File(context.filesDir, "chat-attachments"),
            File(context.filesDir, "datastore/settings.preferences_pb")
        )
        val externalDocuments = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val backups = externalDocuments?.let { File(it, "Chat Backups") }

        return (knownPreferenceFiles + knownPrivateFiles).any(::containsData) ||
            (backups != null && containsData(backups))
    }

    private fun containsData(file: File): Boolean = when {
        !file.exists() -> false
        file.isFile -> file.length() > 0L
        else -> file.listFiles()?.isNotEmpty() == true
    }
}

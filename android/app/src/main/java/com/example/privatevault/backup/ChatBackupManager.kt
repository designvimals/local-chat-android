package com.example.privatevault.backup

import android.content.Context
import android.os.Environment
import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.data.local.TokenStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatBackupManager(private val context: Context) {
    fun createBackup(reason: String): File {
        val tokenStore = TokenStore(context)
        val messages = MessageStore(context, tokenStore).messages.value
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val directory = File(root, "Chat Backups").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        val destination = File(directory, "chat-backup-$reason-$stamp.txt")
        destination.bufferedWriter().use { writer ->
            messages.filter { it.text.isNotBlank() }.forEach { message ->
                val speaker = if (message.senderDeviceId == tokenStore.getDeviceId()) "Me" else "Other device"
                val text = message.text.replace("\r", " ").replace("\n", " ")
                writer.appendLine("[${message.timestamp}] $speaker: $text")
            }
        }
        return destination
    }
}

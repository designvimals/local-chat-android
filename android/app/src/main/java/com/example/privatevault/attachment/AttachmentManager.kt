package com.example.privatevault.attachment

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.privatevault.R
import java.io.File

class AttachmentManager(private val context: Context) {
    fun stageForWeb(uri: Uri): File {
        val requestedName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.takeIf { it.isNotBlank() }
            ?: "attachment"
        val safeName = requestedName.replace(Regex("[\\\\/:*?\"<>|]"), "-")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appFolder = context.getString(R.string.app_name)
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .trim()
            .ifBlank { "Chat App" }
        val directory = File(downloads, "$appFolder Attachments").apply { mkdirs() }
        val destination = uniqueFile(directory, safeName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected file could not be opened." }
            destination.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun uniqueFile(directory: File, name: String): File {
        val initial = File(directory, name)
        if (!initial.exists()) return initial
        val extensionIndex = name.lastIndexOf('.')
        val base = if (extensionIndex > 0) name.substring(0, extensionIndex) else name
        val extension = if (extensionIndex > 0) name.substring(extensionIndex) else ""
        var copy = 2
        while (true) {
            val candidate = File(directory, "$base ($copy)$extension")
            if (!candidate.exists()) return candidate
            copy += 1
        }
    }
}

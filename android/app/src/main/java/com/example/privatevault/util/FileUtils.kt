package com.example.privatevault.util

import android.webkit.MimeTypeMap
import java.io.File

object FileUtils {
    fun mimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    fun displaySize(bytes: Long?): String {
        if (bytes == null) return "Folder"
        if (bytes < 1024) return "$bytes B"
        val kib = bytes / 1024.0
        if (kib < 1024) return "%.1f KB".format(kib)
        val mib = kib / 1024.0
        if (mib < 1024) return "%.1f MB".format(mib)
        return "%.1f GB".format(mib / 1024.0)
    }
}

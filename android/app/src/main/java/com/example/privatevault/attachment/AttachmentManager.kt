package com.example.privatevault.attachment

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.privatevault.model.ChatAttachment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.BufferedOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps only a durable reference to a sender-selected document. The original
 * bytes stay at the selected URI; a receiving phone stores its delivered copy
 * only in app-private storage, never as a second public Downloads file.
 */
class AttachmentManager(private val context: Context) {
    private val preferences = context.getSharedPreferences("chat_attachments", Context.MODE_PRIVATE)
    private val attachmentsDirectory = File(context.filesDir, "chat-attachments").apply { mkdirs() }
    private val receiving = ConcurrentHashMap.newKeySet<String>()
    private val incomingStreams = ConcurrentHashMap<String, BufferedOutputStream>()
    private val _updates = MutableStateFlow(0L)
    val updates: StateFlow<Long> = _updates

    fun registerOriginal(uri: Uri): ChatAttachment {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val metadata = queryMetadata(uri)
        val attachment = ChatAttachment(
            id = "att_${UUID.randomUUID()}",
            name = sanitizeName(metadata.first),
            mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
            size = metadata.second
        )
        preferences.edit().putString(originalKey(attachment.id), uri.toString()).apply()
        notifyChanged()
        return attachment
    }

    fun hasLocalBytes(attachmentId: String): Boolean = originalUri(attachmentId) != null || cachedFile(attachmentId)?.isFile == true
    fun isReceiving(attachmentId: String): Boolean = receiving.contains(attachmentId)

    fun open(attachmentId: String): InputStream {
        originalUri(attachmentId)?.let { uri ->
            context.contentResolver.openInputStream(uri)?.let { return it }
        }
        cachedFile(attachmentId)?.takeIf(File::isFile)?.let { return it.inputStream() }
        error("The original attachment is not available on this device.")
    }

    fun beginIncoming(attachment: ChatAttachment) {
        attachmentsDirectory.mkdirs()
        incomingStreams.remove(attachment.id)?.close()
        incomingTemporary(attachment.id).delete()
        incomingTemporary(attachment.id).createNewFile()
        incomingStreams[attachment.id] = FileOutputStream(incomingTemporary(attachment.id), false).buffered()
        receiving.add(attachment.id)
        preferences.edit()
            .putString(nameKey(attachment.id), sanitizeName(attachment.name))
            .apply()
        notifyChanged()
    }

    fun appendIncomingChunk(attachmentId: String, bytes: ByteArray) {
        val output = requireNotNull(incomingStreams[attachmentId]) { "Attachment transfer was not started." }
        synchronized(output) {
            output.write(bytes)
        }
    }

    fun finishIncoming(attachment: ChatAttachment) {
        val temporary = incomingTemporary(attachment.id)
        incomingStreams.remove(attachment.id)?.let { output ->
            synchronized(output) {
                output.flush()
                output.close()
            }
        }
        require(temporary.isFile) { "Attachment transfer was not started." }
        require(attachment.size < 0 || temporary.length() == attachment.size) { "Attachment transfer was incomplete." }
        val destination = File(attachmentsDirectory, "${attachment.id}-${sanitizeName(attachment.name)}")
        destination.delete()
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        preferences.edit()
            .putString(cacheKey(attachment.id), destination.absolutePath)
            .putString(nameKey(attachment.id), sanitizeName(attachment.name))
            .apply()
        receiving.remove(attachment.id)
        notifyChanged()
    }

    fun discardIncoming(attachmentId: String) {
        incomingStreams.remove(attachmentId)?.close()
        incomingTemporary(attachmentId).delete()
        receiving.remove(attachmentId)
        notifyChanged()
    }

    fun decodePreview(attachmentId: String, targetSizePx: Int): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = originalUri(attachmentId)?.let { ImageDecoder.createSource(context.contentResolver, it) }
                    ?: cachedFile(attachmentId)?.takeIf(File::isFile)?.let(ImageDecoder::createSource)
                    ?: return null
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width.coerceAtLeast(1)
                    val height = info.size.height.coerceAtLeast(1)
                    val scale = minOf(1f, targetSizePx.toFloat() / maxOf(width, height))
                    decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                open(attachmentId).use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long> {
        var name = "attachment"
        var size = -1L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }

    private fun originalUri(id: String): Uri? = preferences.getString(originalKey(id), null)?.let(Uri::parse)
    private fun cachedFile(id: String): File? = preferences.getString(cacheKey(id), null)?.let(::File)
    private fun incomingTemporary(id: String): File = File(attachmentsDirectory, "$id.part")
    private fun originalKey(id: String) = "original:$id"
    private fun cacheKey(id: String) = "cache:$id"
    private fun nameKey(id: String) = "name:$id"
    private fun sanitizeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "-").take(160).ifBlank { "attachment" }
    private fun notifyChanged() { _updates.value += 1 }
}

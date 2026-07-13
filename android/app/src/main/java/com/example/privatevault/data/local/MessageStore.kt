package com.example.privatevault.data.local

import android.content.Context
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.util.TimeUtils
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The phone's authoritative chat transcript. Each line in messages.txt is one
 * JSON message so it remains portable and human-readable without a database.
 */
class MessageStore(
    private val context: Context,
    private val tokenStore: TokenStore
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val transcriptFile = File(context.filesDir, "messages.txt")
    private val _messages = MutableStateFlow(loadMessages())
    val messages: StateFlow<List<Message>> = _messages

    @Synchronized
    fun addLocalMessage(text: String, attachment: ChatAttachment? = null): Message {
        val message = Message(
            id = "msg_${UUID.randomUUID()}",
            senderDeviceId = tokenStore.getDeviceId(),
            receiverDeviceId = CONVERSATION_PEER_ID,
            text = text,
            timestamp = TimeUtils.nowIso(),
            status = "sent",
            attachment = attachment
        )
        replace(_messages.value + message)
        return message
    }

    @Synchronized
    fun receiveMessage(incoming: Message): Message {
        _messages.value.firstOrNull { it.id == incoming.id }?.let { return it }

        val now = TimeUtils.nowIso()
        val message = Message(
            id = incoming.id,
            senderDeviceId = incoming.senderDeviceId,
            receiverDeviceId = tokenStore.getDeviceId(),
            text = incoming.text,
            timestamp = incoming.timestamp,
            status = "delivered",
            deliveredAt = now,
            attachment = incoming.attachment
        )
        replace(_messages.value + message)
        return message
    }

    @Synchronized
    fun markDeliveredTo(receiverDeviceId: String) {
        val now = TimeUtils.nowIso()
        replaceIfChanged { message ->
            if (message.senderDeviceId == tokenStore.getDeviceId() && message.status == "sent") {
                message.copy(status = "delivered", deliveredAt = now)
            } else {
                message
            }
        }
    }

    @Synchronized
    fun markReadBy(readerDeviceId: String, readAt: String = TimeUtils.nowIso()) {
        replaceIfChanged { message ->
            if (message.senderDeviceId == tokenStore.getDeviceId() && message.status != "read") {
                message.copy(
                    status = "read",
                    deliveredAt = message.deliveredAt ?: readAt,
                    readAt = readAt
                )
            } else {
                message
            }
        }
    }

    fun markIncomingReadOnPhone() {
        val now = TimeUtils.nowIso()
        replaceIfChanged { message ->
            if (message.senderDeviceId != tokenStore.getDeviceId() && message.status != "read") {
                message.copy(
                    status = "read",
                    deliveredAt = message.deliveredAt ?: now,
                    readAt = now
                )
            } else {
                message
            }
        }
    }

    @Synchronized
    fun merge(incoming: List<Message>) {
        val merged = _messages.value.associateBy { it.id }.toMutableMap()
        incoming.forEach { candidate ->
            val current = merged[candidate.id]
            merged[candidate.id] = if (current == null || statusRank(candidate.status) >= statusRank(current.status)) {
                candidate
            } else {
                current
            }
        }
        val next = merged.values.sortedBy(Message::timestamp)
        if (next != _messages.value) replace(next)
    }

    @Synchronized
    fun markDelivered(messageId: String, deliveredAt: String = TimeUtils.nowIso()) {
        replaceIfChanged { message ->
            if (message.id == messageId && message.status != "read") {
                message.copy(status = "delivered", deliveredAt = message.deliveredAt ?: deliveredAt)
            } else {
                message
            }
        }
    }

    private fun replaceIfChanged(transform: (Message) -> Message) {
        val current = _messages.value
        val updated = current.map(transform)
        if (updated != current) replace(updated)
    }

    private fun replace(messages: List<Message>) {
        val sorted = messages.sortedBy(Message::timestamp)
        val temporary = File(transcriptFile.parentFile, "${transcriptFile.name}.tmp")
        temporary.bufferedWriter().use { writer ->
            sorted.forEach { message ->
                writer.appendLine(json.encodeToString(message))
            }
        }
        if (!temporary.renameTo(transcriptFile)) {
            temporary.copyTo(transcriptFile, overwrite = true)
            temporary.delete()
        }
        _messages.value = sorted
    }

    private fun loadMessages(): List<Message> {
        if (!transcriptFile.exists()) return emptyList()
        return transcriptFile.useLines { lines ->
            lines.mapNotNull { line ->
                runCatching { json.decodeFromString<Message>(line) }.getOrNull()
            }.sortedBy(Message::timestamp).toList()
        }
    }

    companion object {
        const val VIEWER_DEVICE_ID = "viewer-web"
        const val CONVERSATION_PEER_ID = "conversation-peer"

        private fun statusRank(status: String): Int = when (status) {
            "read" -> 4
            "delivered" -> 3
            "sent" -> 2
            "pending" -> 1
            else -> 0
        }
    }
}

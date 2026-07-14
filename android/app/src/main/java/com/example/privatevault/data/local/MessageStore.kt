package com.example.privatevault.data.local

import android.content.Context
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.DeleteScope
import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import com.example.privatevault.model.MessageReaction
import com.example.privatevault.util.TimeUtils
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val mutationMutex = Mutex()
    val messages: StateFlow<List<Message>> = _messages

    suspend fun addLocalMessage(
        text: String,
        attachment: ChatAttachment? = null,
        emphasisLevel: Int = MessageEmphasis.Normal.storedValue,
        replyToMessageId: String? = null
    ): Message = mutationMutex.withLock {
        val now = TimeUtils.nowIso()
        val replyId = replyToMessageId?.takeIf { id ->
            _messages.value.any { it.id == id && it.deletedAt == null }
        }
        val message = Message(
            id = "msg_${UUID.randomUUID()}",
            senderDeviceId = tokenStore.getDeviceId(),
            receiverDeviceId = CONVERSATION_PEER_ID,
            text = text,
            timestamp = now,
            status = "sent",
            attachment = attachment,
            attachments = listOfNotNull(attachment),
            emphasisLevel = MessageEmphasis.sanitize(emphasisLevel),
            replyToMessageId = replyId,
            updatedAt = now
        )
        persistAndPublish(_messages.value + message)
        message
    }

    suspend fun addLocalAttachments(
        attachments: List<ChatAttachment>,
        caption: String = "",
        replyToMessageId: String? = null
    ): Message = mutationMutex.withLock {
        require(attachments.isNotEmpty()) { "Choose at least one attachment." }
        val replyId = replyToMessageId?.takeIf { id ->
            _messages.value.any { it.id == id && it.deletedAt == null }
        }
        val now = TimeUtils.nowIso()
        val orderedAttachments = attachments.distinctBy(ChatAttachment::id)
        val message = Message(
            id = "msg_${UUID.randomUUID()}",
            senderDeviceId = tokenStore.getDeviceId(),
            receiverDeviceId = CONVERSATION_PEER_ID,
            text = caption.trim(),
            timestamp = now,
            status = "sent",
            attachment = orderedAttachments.first(),
            attachments = orderedAttachments,
            replyToMessageId = replyId,
            updatedAt = now
        )
        persistAndPublish(_messages.value + message)
        message
    }

    suspend fun receiveMessage(incoming: Message): Message = mutationMutex.withLock {
        val now = TimeUtils.nowIso()
        val candidate = MessageMergeRules.normalize(incoming.copy(
            receiverDeviceId = tokenStore.getDeviceId(),
            status = "delivered",
            deliveredAt = incoming.deliveredAt ?: now
        ))
        val existing = _messages.value.firstOrNull { it.id == incoming.id }
        val saved = existing?.let { MessageMergeRules.merge(it, candidate) } ?: candidate
        val next = if (existing == null) {
            _messages.value + saved
        } else {
            _messages.value.map { if (it.id == saved.id) saved else it }
        }
        if (next != _messages.value) persistAndPublish(next)
        saved
    }

    suspend fun markDeliveredTo(receiverDeviceId: String) {
        val now = TimeUtils.nowIso()
        mutate { message ->
            if (message.senderDeviceId == tokenStore.getDeviceId() && message.status == "sent") {
                message.copy(status = "delivered", deliveredAt = now)
            } else {
                message
            }
        }
    }

    suspend fun markReadBy(readerDeviceId: String, readAt: String = TimeUtils.nowIso()) {
        mutate { message ->
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

    suspend fun markIncomingReadOnPhone() {
        val now = TimeUtils.nowIso()
        mutate { message ->
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

    suspend fun toggleReaction(messageId: String, emoji: String) {
        val deviceId = tokenStore.getDeviceId()
        val now = TimeUtils.nowIso()
        mutate { message ->
            if (message.id != messageId || message.deletedAt != null) return@mutate message
            val current = message.reactions.firstOrNull { it.emoji == emoji }
            val reactors = current?.reactorDeviceIds.orEmpty().toMutableSet().apply {
                if (!add(deviceId)) remove(deviceId)
            }
            val next = message.reactions
                .filterNot { it.emoji == emoji }
                .toMutableList()
                .apply {
                    if (reactors.isNotEmpty()) {
                        add(MessageReaction(emoji, reactors))
                    }
                }
            message.copy(reactions = next, updatedAt = now)
        }
    }

    suspend fun merge(incoming: List<Message>) = mutationMutex.withLock {
        val merged = _messages.value.associateBy { it.id }.toMutableMap()
        incoming.forEach { rawCandidate ->
            val candidate = MessageMergeRules.normalize(rawCandidate)
            val current = merged[candidate.id]
            merged[candidate.id] = current?.let { MessageMergeRules.merge(it, candidate) } ?: candidate
        }
        val next = merged.values.sortedBy(Message::timestamp)
        if (next != _messages.value) persistAndPublish(next)
    }

    suspend fun markDelivered(messageId: String, deliveredAt: String = TimeUtils.nowIso()) {
        mutate { message ->
            if (message.id == messageId && message.status != "read") {
                message.copy(status = "delivered", deliveredAt = message.deliveredAt ?: deliveredAt)
            } else {
                message
            }
        }
    }

    fun canEdit(message: Message, now: Instant = Instant.now()): Boolean {
        return MessageMutationRules.canEdit(message, tokenStore.getDeviceId(), now)
    }

    suspend fun editMessage(messageId: String, text: String, now: String = TimeUtils.nowIso()): Message =
        mutationMutex.withLock {
            val current = _messages.value.firstOrNull { it.id == messageId }
                ?: error("Message not found.")
            val edited = MessageMutationRules.edit(current, tokenStore.getDeviceId(), text, now)
            persistAndPublish(_messages.value.map { if (it.id == messageId) edited else it })
            edited
        }

    suspend fun deleteMessages(
        messageIds: Set<String>,
        scope: DeleteScope,
        now: String = TimeUtils.nowIso()
    ): List<Message> = mutationMutex.withLock {
        require(messageIds.isNotEmpty()) { "Select at least one message." }
        val selected = _messages.value.filter { it.id in messageIds }
        require(selected.size == messageIds.size) { "One or more messages no longer exist." }
        val updatedById = MessageMutationRules
            .deleteAll(selected, tokenStore.getDeviceId(), scope, now)
            .associateBy(Message::id)
        persistAndPublish(_messages.value.map { updatedById[it.id] ?: it })
        updatedById.values.toList()
    }

    private suspend fun mutate(transform: (Message) -> Message) = mutationMutex.withLock {
        val current = _messages.value
        val updated = current.map(transform)
        if (updated != current) persistAndPublish(updated)
    }

    private suspend fun persistAndPublish(messages: List<Message>) {
        val sorted = messages.sortedBy(Message::timestamp)
        withContext(Dispatchers.IO) {
            val temporary = File(transcriptFile.parentFile, "${transcriptFile.name}.tmp")
            temporary.bufferedWriter().use { writer ->
                sorted.forEach { message -> writer.appendLine(json.encodeToString(message)) }
            }
            if (!temporary.renameTo(transcriptFile)) {
                temporary.copyTo(transcriptFile, overwrite = true)
                temporary.delete()
            }
        }
        _messages.value = sorted
    }

    private fun loadMessages(): List<Message> {
        if (!transcriptFile.exists()) return emptyList()
        return transcriptFile.useLines { lines ->
            lines.mapNotNull { line ->
                runCatching { json.decodeFromString<Message>(line) }.getOrNull()?.let { message ->
                    MessageMergeRules.normalize(message)
                }
            }.sortedBy(Message::timestamp).toList()
        }
    }

    companion object {
        const val VIEWER_DEVICE_ID = "viewer-web"
        const val CONVERSATION_PEER_ID = "conversation-peer"
    }
}

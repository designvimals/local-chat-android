package com.example.privatevault.data.local

import com.example.privatevault.model.DeleteScope
import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import java.time.Duration
import java.time.Instant

object MessageMutationRules {
    const val EDIT_WINDOW_SECONDS = 30L * 60L

    fun canEdit(message: Message, deviceId: String, now: Instant): Boolean {
        if (message.senderDeviceId != deviceId || message.deletedAt != null || message.text.isBlank()) return false
        return runCatching {
            Duration.between(Instant.parse(message.timestamp), now).seconds in 0..EDIT_WINDOW_SECONDS
        }.getOrDefault(false)
    }

    fun edit(message: Message, deviceId: String, text: String, now: String): Message {
        require(canEdit(message, deviceId, Instant.parse(now))) {
            "Messages can only be edited for 30 minutes."
        }
        val trimmed = text.trim()
        require(trimmed.isNotBlank()) { "Message text cannot be empty." }
        return message.copy(text = trimmed, editedAt = now, updatedAt = now)
    }

    fun deleteAll(
        selected: List<Message>,
        deviceId: String,
        scope: DeleteScope,
        now: String
    ): List<Message> {
        require(selected.isNotEmpty()) { "Select at least one message." }
        if (scope == DeleteScope.ForEveryone) {
            require(selected.all { it.senderDeviceId == deviceId }) {
                "Only your messages can be deleted for both devices."
            }
        }
        return selected.map { message ->
            when (scope) {
                DeleteScope.ForMe -> message.copy(
                    deletedForDeviceIds = message.deletedForDeviceIds + deviceId
                )
                DeleteScope.ForEveryone -> message.copy(
                    text = "",
                    attachment = null,
                    attachments = emptyList(),
                    emphasisLevel = MessageEmphasis.Normal.storedValue,
                    reactions = emptyList(),
                    replyToMessageId = null,
                    editedAt = null,
                    deletedAt = message.deletedAt ?: now,
                    updatedAt = maxOf(message.updatedAt, now)
                )
            }
        }
    }
}

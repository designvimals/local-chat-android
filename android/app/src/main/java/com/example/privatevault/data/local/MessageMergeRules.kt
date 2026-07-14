package com.example.privatevault.data.local

import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import com.example.privatevault.model.canonicalAttachments

object MessageMergeRules {
    fun normalize(message: Message): Message {
        val updatedAt = message.updatedAt.ifBlank { message.timestamp }
        return if (message.deletedAt != null) {
            message.copy(
                text = "",
                attachment = null,
                attachments = emptyList(),
                emphasisLevel = MessageEmphasis.Normal.storedValue,
                reactions = emptyList(),
                replyToMessageId = null,
                editedAt = null,
                updatedAt = maxOf(updatedAt, message.deletedAt)
            )
        } else {
            val attachments = message.canonicalAttachments
            message.copy(
                attachment = attachments.firstOrNull(),
                attachments = attachments,
                emphasisLevel = MessageEmphasis.sanitize(message.emphasisLevel),
                updatedAt = updatedAt
            )
        }
    }

    fun merge(current: Message, candidate: Message): Message {
        val currentNormalized = normalize(current)
        val candidateNormalized = normalize(candidate)
        val latest = when {
            candidateNormalized.updatedAt > currentNormalized.updatedAt -> candidateNormalized
            candidateNormalized.updatedAt < currentNormalized.updatedAt -> currentNormalized
            mutationKey(candidateNormalized) >= mutationKey(currentNormalized) -> candidateNormalized
            else -> currentNormalized
        }
        val status = if (statusRank(candidateNormalized.status) > statusRank(currentNormalized.status)) {
            candidateNormalized.status
        } else {
            currentNormalized.status
        }
        val deletedAt = maxTimestamp(currentNormalized.deletedAt, candidateNormalized.deletedAt)
        return normalize(latest.copy(
            status = status,
            deliveredAt = maxTimestamp(currentNormalized.deliveredAt, candidateNormalized.deliveredAt),
            readAt = maxTimestamp(currentNormalized.readAt, candidateNormalized.readAt),
            deletedAt = deletedAt,
            deletedForDeviceIds = currentNormalized.deletedForDeviceIds + candidateNormalized.deletedForDeviceIds,
            updatedAt = maxOf(latest.updatedAt, deletedAt ?: latest.updatedAt)
        ))
    }

    fun statusRank(status: String): Int = when (status) {
        "read" -> 4
        "delivered" -> 3
        "sent" -> 2
        "pending" -> 1
        else -> 0
    }

    private fun mutationKey(message: Message): String = buildString {
        append(message.deletedAt.orEmpty()).append('|')
        append(message.text).append('|')
        message.canonicalAttachments.forEach { attachment ->
            append(attachment.id).append(':')
            append(attachment.name).append(':')
            append(attachment.mimeType).append(':')
            append(attachment.size).append(':')
            append(attachment.width?.toString().orEmpty()).append('x')
            append(attachment.height?.toString().orEmpty()).append(';')
        }
        append('|')
        append(message.emphasisLevel).append('|')
        append(message.replyToMessageId.orEmpty()).append('|')
        append(message.editedAt.orEmpty()).append('|')
        message.reactions.sortedBy { it.emoji }.forEach { reaction ->
            append(reaction.emoji).append(':')
            append(reaction.reactorDeviceIds.sorted().joinToString(",")).append(';')
        }
    }

    private fun maxTimestamp(first: String?, second: String?): String? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }
}

package com.example.privatevault.data.local

import com.example.privatevault.model.Message
import com.example.privatevault.model.canonicalAttachments

/** Compact, deterministic state used to request only messages that actually changed. */
object MessageSyncRevision {
    fun of(message: Message): String {
        val canonical = buildString {
            append(message.updatedAt).append('|')
            append(message.status).append('|')
            append(message.deliveredAt.orEmpty()).append('|')
            append(message.readAt.orEmpty()).append('|')
            append(message.deletedAt.orEmpty()).append('|')
            append(message.deletedForDeviceIds.sorted().joinToString(",")).append('|')
            append(message.text).append('|')
            append(message.emphasisLevel).append('|')
            append(message.replyToMessageId.orEmpty()).append('|')
            append(message.editedAt.orEmpty()).append('|')
            message.canonicalAttachments.forEach { attachment ->
                append(attachment.id).append(':')
                append(attachment.name).append(':')
                append(attachment.mimeType).append(':')
                append(attachment.size).append(':')
                append(attachment.width?.toString().orEmpty()).append('x')
                append(attachment.height?.toString().orEmpty()).append(';')
            }
            append('|')
            message.reactions.sortedBy { it.emoji }.forEach { reaction ->
                append(reaction.emoji).append(':')
                append(reaction.reactorDeviceIds.sorted().joinToString(",")).append(';')
            }
        }
        return fnv1a(canonical, FNV_OFFSET) + fnv1a(canonical, SECOND_OFFSET)
    }

    private fun fnv1a(value: String, seed: Int): String {
        var hash = seed
        value.forEach { character ->
            hash = hash xor character.code
            hash *= FNV_PRIME
        }
        return Integer.toUnsignedString(hash, 16).padStart(8, '0')
    }

    private const val FNV_OFFSET = -2128831035 // 0x811c9dc5
    private const val SECOND_OFFSET = -1640531527 // 0x9e3779b9
    private const val FNV_PRIME = 16777619
}

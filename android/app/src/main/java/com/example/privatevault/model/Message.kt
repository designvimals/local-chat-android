package com.example.privatevault.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val senderDeviceId: String,
    val receiverDeviceId: String,
    val text: String,
    val timestamp: String,
    val status: String,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val attachment: ChatAttachment? = null,
    val attachments: List<ChatAttachment> = emptyList(),
    val emphasisLevel: Int = MessageEmphasis.Normal.storedValue,
    val reactions: List<MessageReaction> = emptyList(),
    val replyToMessageId: String? = null,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val deletedForDeviceIds: Set<String> = emptySet(),
    val updatedAt: String = timestamp
)

/**
 * Ordered attachment content with compatibility for transcripts written before
 * multi-attachment messages were introduced.
 */
val Message.canonicalAttachments: List<ChatAttachment>
    get() = (attachments + listOfNotNull(attachment)).distinctBy(ChatAttachment::id)

@Serializable
data class MessageReaction(
    val emoji: String,
    val reactorDeviceIds: Set<String> = emptySet()
) {
    val count: Int get() = reactorDeviceIds.size
}

enum class DeleteScope {
    ForMe,
    ForEveryone
}

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
    val emphasisLevel: Int = MessageEmphasis.Normal.storedValue,
    val reactions: List<MessageReaction> = emptyList()
)

@Serializable
data class MessageReaction(
    val emoji: String,
    val reactorDeviceIds: Set<String> = emptySet()
) {
    val count: Int get() = reactorDeviceIds.size
}

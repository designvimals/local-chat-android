package com.example.privatevault.service

import com.example.privatevault.model.Message

/** Dedupe policy kept separate from Android notification APIs for unit testing. */
class IncomingMessageNotificationPolicy(
    private val currentDeviceId: String,
    initialMessages: List<Message>
) {
    private val knownMessageIds = initialMessages.mapTo(mutableSetOf(), Message::id)

    fun onMessagesChanged(messages: List<Message>, chatVisible: Boolean): Boolean {
        val hasNewIncomingMessage = messages.any { message ->
            message.id !in knownMessageIds &&
                message.senderDeviceId != currentDeviceId &&
                message.deletedAt == null
        }
        messages.mapTo(knownMessageIds, Message::id)
        return hasNewIncomingMessage && !chatVisible
    }
}

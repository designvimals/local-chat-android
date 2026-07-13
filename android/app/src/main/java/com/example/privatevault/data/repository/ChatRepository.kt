package com.example.privatevault.data.repository

import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.model.Message
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ChatRepository(
    private val messageStore: MessageStore,
    private val tokenStore: TokenStore
) {
    val messages: StateFlow<List<Message>> = messageStore.messages
    private val _viewerConnected = MutableStateFlow(false)
    val viewerConnected: StateFlow<Boolean> = _viewerConnected

    fun sendMessage(text: String): Message {
        return messageStore.addLocalMessage(text.trim())
    }

    fun receiveMessage(id: String, senderDeviceId: String, text: String, timestamp: String): Message {
        markViewerConnected()
        return messageStore.receiveMessage(id, senderDeviceId, text.trim(), timestamp)
    }

    fun messagesForViewer(): List<Message> {
        markViewerConnected()
        messageStore.markDeliveredTo(MessageStore.VIEWER_DEVICE_ID)
        return messageStore.messages.value
    }

    fun markReadBy(readerDeviceId: String, readAt: String) {
        markViewerConnected()
        messageStore.markReadBy(readerDeviceId, readAt)
    }

    fun markIncomingReadOnPhone() {
        messageStore.markIncomingReadOnPhone()
    }

    fun markViewerConnectedFromRelay() {
        markViewerConnected()
    }

    private fun markViewerConnected() {
        tokenStore.markPairingClaimed()
        _viewerConnected.value = true
    }
}

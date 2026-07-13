package com.example.privatevault.data.repository

import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.model.ChatAttachment
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
    private val _remoteTyping = MutableStateFlow(false)
    val remoteTyping: StateFlow<Boolean> = _remoteTyping
    private val _localTyping = MutableStateFlow(false)

    fun sendMessage(text: String): Message {
        return messageStore.addLocalMessage(text.trim())
    }

    fun sendAttachment(attachment: ChatAttachment, caption: String = ""): Message {
        return messageStore.addLocalMessage(caption.trim(), attachment)
    }

    fun receiveMessage(incoming: Message): Message {
        markViewerConnected()
        return messageStore.receiveMessage(incoming.copy(text = incoming.text.trim()))
    }

    fun messagesForViewer(readerDeviceId: String = MessageStore.VIEWER_DEVICE_ID): List<Message> {
        markViewerConnected()
        messageStore.markDeliveredTo(readerDeviceId)
        return messageStore.messages.value
    }

    fun mergeFromPeer(messages: List<Message>) {
        messageStore.merge(messages)
    }

    fun pendingForPeer(): List<Message> {
        val ownDeviceId = tokenStore.getDeviceId()
        return messageStore.messages.value.filter { message ->
            message.senderDeviceId == ownDeviceId && message.status in setOf("pending", "sent", "failed")
        }
    }

    fun markPeerMessageDelivered(messageId: String, deliveredAt: String) {
        messageStore.markDelivered(messageId, deliveredAt)
    }

    fun isMine(message: Message): Boolean = message.senderDeviceId == tokenStore.getDeviceId()

    fun setPeerConnected(connected: Boolean) {
        _viewerConnected.value = connected || tokenStore.isPairingClaimed()
        if (!connected) _remoteTyping.value = false
    }

    fun setLocalTyping(typing: Boolean) { _localTyping.value = typing }
    fun isLocalTyping(): Boolean = _localTyping.value
    fun setRemoteTyping(typing: Boolean) { _remoteTyping.value = typing }

    fun attachment(attachmentId: String): ChatAttachment? = messages.value
        .asSequence()
        .mapNotNull(Message::attachment)
        .firstOrNull { it.id == attachmentId }

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

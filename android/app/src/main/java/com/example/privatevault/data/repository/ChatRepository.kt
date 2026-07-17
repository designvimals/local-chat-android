package com.example.privatevault.data.repository

import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.DeleteScope
import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import com.example.privatevault.model.MessageReaction
import com.example.privatevault.model.canonicalAttachments
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ChatRepository(
    private val messageStore: MessageStore,
    private val tokenStore: TokenStore
) {
    val messages: StateFlow<List<Message>> = messageStore.messages
    private val _viewerConnected = MutableStateFlow(false)
    val viewerConnected: StateFlow<Boolean> = _viewerConnected
    private val _peerPresence = MutableStateFlow(
        PeerPresence(lastSeenAtMillis = tokenStore.getPeerLastSeenAtMillis())
    )
    val peerPresence: StateFlow<PeerPresence> = _peerPresence
    private val _remoteTyping = MutableStateFlow(false)
    val remoteTyping: StateFlow<Boolean> = _remoteTyping
    private val _localTyping = MutableStateFlow(false)

    suspend fun sendMessage(
        text: String,
        emphasisLevel: Int = MessageEmphasis.Normal.storedValue,
        replyToMessageId: String? = null
    ): Message {
        return messageStore.addLocalMessage(
            text.trim(),
            emphasisLevel = emphasisLevel,
            replyToMessageId = replyToMessageId
        )
    }

    suspend fun sendAttachment(
        attachment: ChatAttachment,
        caption: String = "",
        replyToMessageId: String? = null
    ): Message {
        return messageStore.addLocalMessage(
            caption.trim(),
            attachment,
            replyToMessageId = replyToMessageId
        )
    }

    suspend fun sendAttachments(
        attachments: List<ChatAttachment>,
        caption: String = "",
        replyToMessageId: String? = null
    ): Message = messageStore.addLocalAttachments(attachments, caption, replyToMessageId)

    suspend fun toggleReaction(messageId: String, emoji: String) {
        messageStore.toggleReaction(messageId, emoji)
    }

    suspend fun editMessage(messageId: String, text: String): Message =
        messageStore.editMessage(messageId, text)

    suspend fun deleteMessages(messageIds: Set<String>, scope: DeleteScope): List<Message> =
        messageStore.deleteMessages(messageIds, scope)

    fun canEdit(message: Message): Boolean = messageStore.canEdit(message)

    fun currentDeviceId(): String = tokenStore.getDeviceId()

    fun isCurrentUserReaction(reaction: MessageReaction): Boolean =
        tokenStore.getDeviceId() in reaction.reactorDeviceIds

    suspend fun receiveMessage(incoming: Message): Message {
        markViewerConnected()
        return messageStore.receiveMessage(incoming.copy(text = incoming.text.trim()))
    }

    suspend fun messagesForViewer(readerDeviceId: String = MessageStore.VIEWER_DEVICE_ID): List<Message> {
        markViewerConnected()
        messageStore.markDeliveredTo(readerDeviceId)
        return messageStore.messages.value
    }

    suspend fun mergeFromPeer(messages: List<Message>) {
        messageStore.merge(messages)
    }

    suspend fun markPeerMessageDelivered(messageId: String, deliveredAt: String) {
        messageStore.markDelivered(messageId, deliveredAt)
    }

    fun isMine(message: Message): Boolean = message.senderDeviceId == tokenStore.getDeviceId()

    @Synchronized
    fun setPeerConnected(connected: Boolean, observedAtMillis: Long = System.currentTimeMillis()) {
        val currentPresence = _peerPresence.value
        val nextPresence = reducePeerPresence(currentPresence, connected, observedAtMillis)
        _peerPresence.value = nextPresence
        if (nextPresence.lastSeenAtMillis != currentPresence.lastSeenAtMillis) {
            tokenStore.setPeerLastSeenAtMillis(requireNotNull(nextPresence.lastSeenAtMillis))
        }
        _viewerConnected.value = connected || tokenStore.isPairingClaimed()
        if (!connected) _remoteTyping.value = false
    }

    fun setLocalTyping(typing: Boolean) { _localTyping.value = typing }
    fun isLocalTyping(): Boolean = _localTyping.value
    fun setRemoteTyping(typing: Boolean) { _remoteTyping.value = typing }

    fun attachment(attachmentId: String): ChatAttachment? = messages.value
        .asSequence()
        .flatMap { it.canonicalAttachments.asSequence() }
        .firstOrNull { it.id == attachmentId }

    suspend fun markReadBy(readerDeviceId: String, readAt: String) {
        markViewerConnected()
        messageStore.markReadBy(readerDeviceId, readAt)
    }

    suspend fun markIncomingReadOnPhone() {
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

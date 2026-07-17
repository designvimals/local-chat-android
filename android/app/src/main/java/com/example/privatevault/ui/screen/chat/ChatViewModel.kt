package com.example.privatevault.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.model.DeleteScope
import com.example.privatevault.model.Message
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.MessageReaction
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(private val chatRepository: ChatRepository) : ViewModel() {
    val messages: StateFlow<List<Message>> = chatRepository.messages
    val viewerConnected: StateFlow<Boolean> = chatRepository.viewerConnected
    val peerPresence = chatRepository.peerPresence
    val remoteTyping: StateFlow<Boolean> = chatRepository.remoteTyping
    private var typingTimeout: Job? = null

    suspend fun send(
        text: String,
        emphasisLevel: Int = 0,
        replyToMessageId: String? = null
    ): Result<Message> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotBlank()) { "Type a message before sending." }
        chatRepository.sendMessage(trimmed, emphasisLevel, replyToMessageId).also { stopTyping() }
    }

    suspend fun sendAttachment(
        attachment: ChatAttachment,
        caption: String = "",
        replyToMessageId: String? = null
    ): Result<Message> = runCatching {
        chatRepository.sendAttachment(attachment, caption, replyToMessageId).also { stopTyping() }
    }

    suspend fun sendAttachments(
        attachments: List<ChatAttachment>,
        caption: String = "",
        replyToMessageId: String? = null
    ): Result<Message> = runCatching {
        require(attachments.isNotEmpty()) { "Choose at least one attachment." }
        chatRepository.sendAttachments(attachments, caption, replyToMessageId).also { stopTyping() }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch { chatRepository.toggleReaction(messageId, emoji) }
    }

    suspend fun editMessage(messageId: String, text: String): Result<Message> =
        runCatching { chatRepository.editMessage(messageId, text) }

    suspend fun deleteMessages(messageIds: Set<String>, scope: DeleteScope): Result<List<Message>> =
        runCatching { chatRepository.deleteMessages(messageIds, scope) }

    fun canEdit(message: Message): Boolean = chatRepository.canEdit(message)

    fun currentDeviceId(): String = chatRepository.currentDeviceId()

    fun isCurrentUserReaction(reaction: MessageReaction): Boolean =
        chatRepository.isCurrentUserReaction(reaction)

    fun composerChanged(text: String) {
        val typing = text.isNotBlank()
        chatRepository.setLocalTyping(typing)
        typingTimeout?.cancel()
        if (typing) {
            typingTimeout = viewModelScope.launch {
                delay(2_500)
                chatRepository.setLocalTyping(false)
            }
        }
    }

    fun stopTyping() {
        typingTimeout?.cancel()
        typingTimeout = null
        chatRepository.setLocalTyping(false)
    }

    override fun onCleared() {
        stopTyping()
        super.onCleared()
    }

    fun markIncomingRead() {
        viewModelScope.launch { chatRepository.markIncomingReadOnPhone() }
    }

    fun isMine(message: Message): Boolean = chatRepository.isMine(message)
}

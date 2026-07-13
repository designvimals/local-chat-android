package com.example.privatevault.ui.screen.chat

import androidx.lifecycle.ViewModel
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.model.Message
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel(private val chatRepository: ChatRepository) : ViewModel() {
    val messages: StateFlow<List<Message>> = chatRepository.messages
    val viewerConnected: StateFlow<Boolean> = chatRepository.viewerConnected

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            chatRepository.sendMessage(trimmed)
        }
    }

    fun markIncomingRead() {
        chatRepository.markIncomingReadOnPhone()
    }

    fun isMine(message: Message): Boolean = chatRepository.isMine(message)
}

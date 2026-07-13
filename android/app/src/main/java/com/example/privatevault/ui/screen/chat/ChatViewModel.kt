package com.example.privatevault.ui.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.model.Message
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(private val chatRepository: ChatRepository) : ViewModel() {
    val messages: StateFlow<List<Message>> = chatRepository.messages
    val viewerConnected: StateFlow<Boolean> = chatRepository.viewerConnected
    val remoteTyping: StateFlow<Boolean> = chatRepository.remoteTyping
    private var typingTimeout: Job? = null

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank()) {
            chatRepository.sendMessage(trimmed)
            stopTyping()
        }
    }

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
        chatRepository.markIncomingReadOnPhone()
    }

    fun isMine(message: Message): Boolean = chatRepository.isMine(message)
}

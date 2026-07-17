package com.example.privatevault.ui.screen.chat

import com.example.privatevault.model.Message
import com.example.privatevault.model.canonicalAttachments

/** Returns matching message IDs in transcript order without changing the visible list. */
internal fun chatSearchMessageIds(messages: List<Message>, rawQuery: String): List<String> {
    val query = rawQuery.trim()
    if (query.isEmpty()) return emptyList()

    return messages.asSequence()
        .filter { it.deletedAt == null }
        .filter { message ->
            message.text.contains(query, ignoreCase = true) ||
                message.canonicalAttachments.any { attachment ->
                    attachment.name.contains(query, ignoreCase = true) ||
                        attachment.mimeType.contains(query, ignoreCase = true)
                }
        }
        .map(Message::id)
        .toList()
}

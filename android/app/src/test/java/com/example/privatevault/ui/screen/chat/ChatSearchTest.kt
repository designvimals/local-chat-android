package com.example.privatevault.ui.screen.chat

import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSearchTest {
    @Test
    fun `blank query returns no results`() {
        assertEquals(emptyList<String>(), chatSearchMessageIds(listOf(message("one", "hello")), "  "))
    }

    @Test
    fun `text search is case insensitive and keeps transcript order`() {
        val messages = listOf(
            message("one", "First HELLO"),
            message("two", "no match"),
            message("three", "hello again")
        )

        assertEquals(listOf("one", "three"), chatSearchMessageIds(messages, "hello"))
    }

    @Test
    fun `attachment names and types are searchable`() {
        val attachment = ChatAttachment(
            id = "file-one",
            name = "Summer Photo.jpg",
            mimeType = "image/jpeg",
            size = 120L
        )
        val message = message("one", "", attachment)

        assertEquals(listOf("one"), chatSearchMessageIds(listOf(message), "summer"))
        assertEquals(listOf("one"), chatSearchMessageIds(listOf(message), "jpeg"))
    }

    @Test
    fun `global tombstones never appear in search`() {
        val deleted = message("deleted", "secret").copy(deletedAt = "2026-07-17T12:00:00Z")

        assertEquals(emptyList<String>(), chatSearchMessageIds(listOf(deleted), "secret"))
    }

    private fun message(id: String, text: String, attachment: ChatAttachment? = null) = Message(
        id = id,
        senderDeviceId = "phone",
        receiverDeviceId = "peer",
        text = text,
        timestamp = "2026-07-17T10:00:00Z",
        status = "sent",
        attachment = attachment
    )
}

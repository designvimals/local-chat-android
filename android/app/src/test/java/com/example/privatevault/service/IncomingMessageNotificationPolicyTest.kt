package com.example.privatevault.service

import com.example.privatevault.model.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingMessageNotificationPolicyTest {
    @Test
    fun `new incoming message notifies only while chat is hidden`() {
        val existing = message("existing", sender = "friend")
        val incoming = message("incoming", sender = "friend")
        val policy = IncomingMessageNotificationPolicy("me", listOf(existing))

        assertTrue(policy.onMessagesChanged(listOf(existing, incoming), chatVisible = false))
        assertFalse(policy.onMessagesChanged(listOf(existing, incoming), chatVisible = false))
    }

    @Test
    fun `visible chat and outgoing messages do not notify`() {
        val visibleIncomingPolicy = IncomingMessageNotificationPolicy("me", emptyList())
        val outgoingPolicy = IncomingMessageNotificationPolicy("me", emptyList())

        assertFalse(
            visibleIncomingPolicy.onMessagesChanged(
                listOf(message("incoming", sender = "friend")),
                chatVisible = true
            )
        )
        assertFalse(
            outgoingPolicy.onMessagesChanged(
                listOf(message("outgoing", sender = "me")),
                chatVisible = false
            )
        )
    }

    private fun message(id: String, sender: String) = Message(
        id = id,
        senderDeviceId = sender,
        receiverDeviceId = "peer",
        text = "private text",
        timestamp = "2026-07-15T12:00:00Z",
        status = "delivered"
    )
}

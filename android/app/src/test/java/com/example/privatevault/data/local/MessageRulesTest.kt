package com.example.privatevault.data.local

import com.example.privatevault.model.DeleteScope
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.model.canonicalAttachments
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRulesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldJsonDefaultsNewFields() {
        val oldJson = """{"id":"one","senderDeviceId":"phone","receiverDeviceId":"web","text":"hello","timestamp":"2026-07-14T10:00:00Z","status":"sent","attachment":{"id":"photo","name":"photo.jpg","mimeType":"image/jpeg","size":123}}"""
        val message = json.decodeFromString<Message>(oldJson)

        assertEquals(message.timestamp, message.updatedAt)
        assertNull(message.replyToMessageId)
        assertNull(message.deletedAt)
        assertTrue(message.deletedForDeviceIds.isEmpty())
        assertNull(message.attachment?.width)
        assertNull(message.attachment?.height)
        assertEquals(listOf("photo"), message.canonicalAttachments.map { it.id })
    }

    @Test
    fun tombstoneCannotBeResurrectedByNewerStaleContent() {
        val deleted = message("one", "phone", "before").copy(
            text = "",
            deletedAt = "2026-07-14T10:05:00Z",
            updatedAt = "2026-07-14T10:05:00Z"
        )
        val staleClient = message("one", "phone", "resurrected").copy(
            status = "read",
            updatedAt = "2026-07-14T10:06:00Z",
            deletedForDeviceIds = setOf("tablet")
        )

        val merged = MessageMergeRules.merge(deleted, staleClient)

        assertEquals("", merged.text)
        assertEquals(deleted.deletedAt, merged.deletedAt)
        assertEquals("read", merged.status)
        assertEquals(setOf("tablet"), merged.deletedForDeviceIds)
    }

    @Test
    fun equalTimestampMergeIsDeterministic() {
        val first = message("one", "phone", "alpha").copy(updatedAt = "2026-07-14T10:02:00Z")
        val second = message("one", "phone", "beta").copy(updatedAt = "2026-07-14T10:02:00Z")

        assertEquals(MessageMergeRules.merge(first, second), MessageMergeRules.merge(second, first))
    }

    @Test
    fun editBoundaryIsInclusiveAtThirtyMinutes() {
        val original = message("one", "phone", "caption")
        assertTrue(MessageMutationRules.canEdit(original, "phone", Instant.parse("2026-07-14T10:30:00Z")))
        assertFalse(MessageMutationRules.canEdit(original, "phone", Instant.parse("2026-07-14T10:30:01Z")))
        assertFalse(MessageMutationRules.canEdit(original, "web", Instant.parse("2026-07-14T10:10:00Z")))
    }

    @Test
    fun localAndGlobalDeleteHaveDifferentPersistence() {
        val original = message("one", "phone", "hello").copy(replyToMessageId = "zero")
        val local = MessageMutationRules.deleteAll(
            listOf(original), "phone", DeleteScope.ForMe, "2026-07-14T10:05:00Z"
        ).single()
        assertEquals("hello", local.text)
        assertEquals(setOf("phone"), local.deletedForDeviceIds)
        assertEquals(original.updatedAt, local.updatedAt)

        val global = MessageMutationRules.deleteAll(
            listOf(original), "phone", DeleteScope.ForEveryone, "2026-07-14T10:05:00Z"
        ).single()
        assertEquals("", global.text)
        assertNull(global.replyToMessageId)
        assertTrue(global.reactions.isEmpty())
        assertEquals("2026-07-14T10:05:00Z", global.deletedAt)
    }

    @Test
    fun mixedSelectionCannotDeleteForEveryone() {
        assertThrows(IllegalArgumentException::class.java) {
            MessageMutationRules.deleteAll(
                listOf(message("mine", "phone", "a"), message("theirs", "web", "b")),
                "phone",
                DeleteScope.ForEveryone,
                "2026-07-14T10:05:00Z"
            )
        }
    }

    @Test
    fun deletingOriginalDoesNotEraseExistingReplyReference() {
        val original = message("original", "phone", "hello")
        val reply = message("reply", "web", "answer").copy(replyToMessageId = original.id)
        val tombstone = MessageMutationRules.deleteAll(
            listOf(original), "phone", DeleteScope.ForEveryone, "2026-07-14T10:05:00Z"
        ).single()

        assertEquals("original", reply.replyToMessageId)
        assertTrue(tombstone.deletedAt != null)
    }

    @Test
    fun canonicalAttachmentsPreserveOrderAndDeduplicateLegacyFallback() {
        val first = attachment("first")
        val second = attachment("second")
        val message = message("gallery", "phone", "caption").copy(
            attachment = first,
            attachments = listOf(first, second, first)
        )

        assertEquals(listOf("first", "second"), message.canonicalAttachments.map { it.id })
        val normalized = MessageMergeRules.normalize(message)
        assertEquals(first, normalized.attachment)
        assertEquals(listOf(first, second), normalized.attachments)
    }

    @Test
    fun globalDeleteClearsEveryAttachmentRepresentation() {
        val first = attachment("first")
        val second = attachment("second")
        val deleted = MessageMutationRules.deleteAll(
            listOf(message("gallery", "phone", "caption").copy(
                attachment = first,
                attachments = listOf(first, second)
            )),
            "phone",
            DeleteScope.ForEveryone,
            "2026-07-14T10:05:00Z"
        ).single()

        assertNull(deleted.attachment)
        assertTrue(deleted.attachments.isEmpty())
        assertTrue(deleted.canonicalAttachments.isEmpty())
    }

    @Test
    fun syncRevisionChangesForContentReceiptsAndLocalDeletion() {
        val original = message("one", "phone", "hello")
        val revision = MessageSyncRevision.of(original)
        assertEquals("7bbbed98b8f37154", revision)

        assertFalse(revision == MessageSyncRevision.of(original.copy(
            text = "changed",
            updatedAt = "2026-07-14T10:01:00Z"
        )))
        assertFalse(revision == MessageSyncRevision.of(original.copy(
            status = "read",
            readAt = "2026-07-14T10:01:00Z"
        )))
        assertFalse(revision == MessageSyncRevision.of(original.copy(
            deletedForDeviceIds = setOf("phone")
        )))
        assertEquals(revision, MessageSyncRevision.of(original.copy()))
    }

    private fun message(id: String, sender: String, text: String) = Message(
        id = id,
        senderDeviceId = sender,
        receiverDeviceId = "peer",
        text = text,
        timestamp = "2026-07-14T10:00:00Z",
        status = "sent"
    )

    private fun attachment(id: String) = ChatAttachment(
        id = id,
        name = "$id.jpg",
        mimeType = "image/jpeg",
        size = 123
    )
}

package com.example.privatevault.ui.screen.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.model.Message
import org.junit.Rule
import org.junit.Test

class MessageBubbleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longPressUsesAppSelectionState() {
        var selected by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                BubbleForTest(selected = selected, selectionCount = if (selected) 1 else 0, showMenu = false) {
                    selected = true
                }
            }
        }

        compose.onNodeWithTag("message-one").performTouchInput { longClick() }
        compose.onNodeWithTag("message-one").assertIsSelected()
    }

    @Test fun multiSelectionMenuOnlyOffersDelete() {
        compose.setContent {
            MaterialTheme { BubbleForTest(selected = true, selectionCount = 2, showMenu = true) {} }
        }

        compose.onNodeWithText("Delete").assertExists()
        compose.onNodeWithText("Reply").assertDoesNotExist()
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test fun readMetadataStaysHiddenUntilTheMessageIsTapped() {
        val readMessage = Message(
            id = "one",
            senderDeviceId = "phone",
            receiverDeviceId = "peer",
            text = "hello",
            timestamp = "2026-07-14T10:00:00Z",
            status = "read",
            readAt = "2026-07-14T10:01:00Z"
        )
        compose.setContent {
            MaterialTheme { BubbleForTest(message = readMessage) {} }
        }

        compose.onNodeWithText("Sent", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Read", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("message-one").performClick()
        compose.onNodeWithText("Sent", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Read", substring = true, useUnmergedTree = true).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun BubbleForTest(
        selected: Boolean = false,
        selectionCount: Int = 0,
        showMenu: Boolean = false,
        message: Message = Message("one", "phone", "peer", "hello", "2026-07-14T10:00:00Z", "sent"),
        onSelect: () -> Unit
    ) {
        MessageBubble(
            message = message,
            isMine = true,
            showSenderName = false,
            showAvatar = false,
            groupedWithPrevious = false,
            groupedWithNext = false,
            reactedByMe = emptySet(),
            selected = selected,
            selectionCount = selectionCount,
            selectionMode = selected,
            showContextMenu = showMenu,
            replyMessage = null,
            replyMessageIsMine = false,
            canEdit = true,
            highlighted = false,
            onToggleReaction = {},
            onToggleSelection = {},
            onSelect = onSelect,
            onDismissContextMenu = {},
            onReply = {},
            onEdit = {},
            onDelete = {},
            onReplyQuoteClick = {},
            onImageClick = {},
            imageModifier = { androidx.compose.ui.Modifier },
            attachmentManager = AttachmentManager(ApplicationProvider.getApplicationContext()),
            playExpressiveOnAppear = false,
            expressiveMotionEnabled = false
        )
    }
}

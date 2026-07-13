package com.example.privatevault.ui.screen.chat

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.privatevault.R
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    attachmentManager: AttachmentManager,
    attachmentVersion: Long,
    modifier: Modifier = Modifier
) {
    val sender = if (isMine) stringResource(R.string.message_from_you)
    else stringResource(R.string.message_from_contact, stringResource(R.string.contact_name))
    val time = TimeUtils.display(message.timestamp)
    val status = if (isMine) receiptStatus(message.status) else ""
    val attachmentDescription = message.attachment?.let {
        if (it.mimeType.startsWith("image/")) stringResource(R.string.image_attachment, it.name)
        else stringResource(R.string.file_attachment, it.name)
    }.orEmpty()
    val accessibleContent = listOf(message.text, attachmentDescription).filter(String::isNotBlank).joinToString(". ")
    val accessibilitySummary = stringResource(
        R.string.message_accessibility,
        sender,
        accessibleContent,
        time,
        status
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = accessibilitySummary }
    ) {
        val bubbleMaxWidth = maxWidth * 0.84f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.widthIn(max = bubbleMaxWidth),
                color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                shape = if (isMine) RoundedCornerShape(26.dp, 26.dp, 7.dp, 26.dp)
                else RoundedCornerShape(26.dp, 26.dp, 26.dp, 7.dp),
                tonalElevation = if (isMine) 2.dp else 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    message.attachment?.let { attachment ->
                        AttachmentContent(
                            attachment = attachment,
                            attachmentManager = attachmentManager,
                            attachmentVersion = attachmentVersion
                        )
                    }
                    if (message.text.isNotBlank()) {
                        SelectionContainer {
                            Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isMine) {
                            Spacer(Modifier.width(5.dp))
                            ReceiptIcon(message.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentContent(
    attachment: ChatAttachment,
    attachmentManager: AttachmentManager,
    attachmentVersion: Long
) {
    val context = LocalContext.current
    if (attachment.mimeType.startsWith("image/")) {
        val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id, attachmentVersion) {
            value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 1_440) }
        }
        val hasLocalBytes = attachmentManager.hasLocalBytes(attachment.id)
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit
            )
        } else if (attachmentManager.isReceiving(attachment.id)) {
            AttachmentPlaceholder(attachment)
        } else if (hasLocalBytes) {
            FileAttachmentCard(attachment)
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(attachment.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.attachment_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        FileAttachmentCard(attachment)
    }
}

@Composable
private fun FileAttachmentCard(attachment: ChatAttachment) {
    val context = LocalContext.current
    Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(attachment.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (attachment.size >= 0) {
                        Text(
                            Formatter.formatShortFileSize(context, attachment.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
}

@Composable
private fun AttachmentPlaceholder(attachment: ChatAttachment) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.attachment_loading), style = MaterialTheme.typography.labelMedium)
            Text(
                attachment.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReceiptIcon(status: String) {
    val icon = when (status) {
        "read", "delivered" -> Icons.Default.DoneAll
        "sent" -> Icons.Default.Done
        else -> Icons.Default.Schedule
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = if (status == "read") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun receiptStatus(status: String): String = when (status) {
    "read" -> stringResource(R.string.receipt_status_read)
    "delivered" -> stringResource(R.string.receipt_status_delivered)
    "sent" -> stringResource(R.string.receipt_status_sent)
    else -> stringResource(R.string.receipt_status_waiting)
}

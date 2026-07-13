@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.privatevault.ui.screen.chat

import android.animation.ValueAnimator
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privatevault.R
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import com.example.privatevault.model.MessageReaction
import com.example.privatevault.ui.theme.ChatExpressiveTokens
import com.example.privatevault.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val ReactionChoices = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    showSenderName: Boolean,
    showAvatar: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    reactedByMe: Set<String>,
    onToggleReaction: (String) -> Unit,
    onImageClick: (ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    animateImageEntrance: Boolean,
    attachmentManager: AttachmentManager,
    attachmentVersion: Long,
    modifier: Modifier = Modifier
) {
    val sender = if (isMine) stringResource(R.string.message_from_you)
    else stringResource(R.string.message_from_contact, stringResource(R.string.contact_name))
    val time = TimeUtils.display(message.timestamp)
    val status = if (isMine) receiptStatus(message.status) else ""
    val emphasis = MessageEmphasis.fromStored(message.emphasisLevel)
    val attachmentDescription = message.attachment?.let {
        if (it.mimeType.startsWith("image/")) stringResource(R.string.image_attachment, it.name)
        else stringResource(R.string.file_attachment, it.name)
    }.orEmpty()
    val accessibleContent = listOf(message.text, attachmentDescription)
        .filter(String::isNotBlank)
        .joinToString(". ")
    val accessibilitySummary = stringResource(
        R.string.message_accessibility,
        sender,
        accessibleContent,
        time,
        status
    )
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    var reactionPickerVisible by remember(message.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (groupedWithPrevious) 1.dp else 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = accessibilitySummary },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (showSenderName && !isMine) {
            Text(
                stringResource(R.string.contact_name),
                modifier = Modifier.padding(start = 58.dp, bottom = 7.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMine) {
                Box(Modifier.width(46.dp), contentAlignment = Alignment.BottomStart) {
                    if (showAvatar) ParticipantAvatar()
                }
                Spacer(Modifier.width(6.dp))
            }
            Box {
                Box(
                    modifier = Modifier.combinedClickable(
                        role = Role.Button,
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            reactionPickerVisible = true
                        }
                    )
                ) {
                    if (emphasis == MessageEmphasis.Maximum &&
                        message.attachment == null && message.text.length <= 34
                    ) {
                        MaximumExpressiveMessage(message.text, time, message.status, isMine)
                    } else {
                        ConventionalBubble(
                            message = message,
                            isMine = isMine,
                            time = time,
                            emphasis = emphasis,
                            groupedWithPrevious = groupedWithPrevious,
                            groupedWithNext = groupedWithNext,
                            animationsEnabled = animationsEnabled,
                            onImageClick = onImageClick,
                            imageModifier = imageModifier,
                            animateImageEntrance = animateImageEntrance,
                            attachmentManager = attachmentManager,
                            attachmentVersion = attachmentVersion
                        )
                    }
                }
                ReactionPicker(
                    expanded = reactionPickerVisible,
                    onDismiss = { reactionPickerVisible = false },
                    onReaction = {
                        reactionPickerVisible = false
                        onToggleReaction(it)
                    }
                )
            }
        }
        if (message.reactions.isNotEmpty()) {
            ReactionPills(
                reactions = message.reactions,
                reactedByMe = reactedByMe,
                onToggleReaction = onToggleReaction,
                modifier = Modifier.padding(
                    start = if (isMine) 0.dp else 58.dp,
                    end = if (isMine) 8.dp else 0.dp
                ).offset(y = (-5).dp)
            )
        }
    }
}

@Composable
private fun ParticipantAvatar() {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.contact_initial).take(1).uppercase(),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConventionalBubble(
    message: Message,
    isMine: Boolean,
    time: String,
    emphasis: MessageEmphasis,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    animationsEnabled: Boolean,
    onImageClick: (ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    animateImageEntrance: Boolean,
    attachmentManager: AttachmentManager,
    attachmentVersion: Long
) {
    BoxWithConstraints {
        val maxBubbleWidth = maxWidth * ChatExpressiveTokens.MessageMaxWidthFraction
        val metrics = messageVisualMetrics(
            text = message.text,
            progress = emphasis.progress,
            baseStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp, lineHeight = 28.sp)
        )
        val shape = bubbleShape(isMine, groupedWithPrevious, groupedWithNext)
        Surface(
            modifier = Modifier.widthIn(max = maxBubbleWidth),
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
            contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
            tonalElevation = if (isMine) 1.dp else 0.dp
        ) {
            Column(
                modifier = Modifier
                    .animateContentSize(if (animationsEnabled) spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ) else snap())
                    .padding(
                        horizontal = if (message.attachment != null) 5.dp else metrics.horizontalPadding,
                        vertical = if (message.attachment != null) 5.dp else metrics.verticalPadding
                    ),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                message.attachment?.let { attachment ->
                    AttachmentContent(
                        attachment = attachment,
                        attachmentManager = attachmentManager,
                        attachmentVersion = attachmentVersion,
                        imageModifier = imageModifier(attachment),
                        onImageClick = { onImageClick(attachment) },
                        animateEntrance = animateImageEntrance && animationsEnabled
                    )
                }
                if (message.text.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            message.text,
                            modifier = Modifier.padding(
                                horizontal = if (message.attachment != null) 10.dp else 0.dp,
                                vertical = if (message.attachment != null) 3.dp else 0.dp
                            ),
                            style = metrics.textStyle
                        )
                    }
                }
                LinkPreviewLoader.firstUrl(message.text)?.let { url ->
                    val preview by produceState(LinkPreviewLoader.fallback(url), url) {
                        value = LinkPreviewLoader.load(url)
                    }
                    LinkPreviewCard(preview)
                }
                MessageMeta(
                    time,
                    message.status,
                    isMine,
                    Modifier.align(Alignment.End).padding(horizontal = if (message.attachment != null) 7.dp else 0.dp)
                )
            }
        }
    }
}

@Composable
private fun MaximumExpressiveMessage(text: String, time: String, status: String, isMine: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.widthIn(max = 350.dp).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = "✦",
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-14).dp),
                fontSize = 24.sp,
                color = colors.tertiary
            )
            Text(
                text = text,
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(colors.primary, colors.tertiary)),
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = when {
                        text.length <= 8 -> 68.sp
                        text.length <= 18 -> 54.sp
                        else -> 42.sp
                    },
                    lineHeight = when {
                        text.length <= 8 -> 72.sp
                        text.length <= 18 -> 58.sp
                        else -> 47.sp
                    },
                    shadow = Shadow(colors.primary.copy(alpha = 0.22f), blurRadius = 18f)
                )
            )
            Text(
                text = "✦",
                modifier = Modifier.align(Alignment.BottomStart).offset(x = (-13).dp, y = 12.dp),
                fontSize = 18.sp,
                color = colors.primary.copy(alpha = 0.72f)
            )
        }
        MessageMeta(time, status, isMine)
    }
}

private fun bubbleShape(isMine: Boolean, previous: Boolean, next: Boolean): RoundedCornerShape {
    val large = 28.dp
    val joined = 9.dp
    return if (isMine) {
        RoundedCornerShape(
            topStart = large,
            topEnd = if (previous) joined else large,
            bottomEnd = if (next) joined else 7.dp,
            bottomStart = large
        )
    } else {
        RoundedCornerShape(
            topStart = if (previous) joined else large,
            topEnd = large,
            bottomEnd = large,
            bottomStart = if (next) joined else 7.dp
        )
    }
}

@Composable
private fun ReactionPicker(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            ReactionChoices.forEach { emoji ->
                val description = stringResource(R.string.insert_emoji, emoji)
                IconButton(
                    onClick = { onReaction(emoji) },
                    modifier = Modifier.size(46.dp).semantics { contentDescription = description }
                ) { Text(emoji, fontSize = 23.sp) }
            }
        }
    }
}

@Composable
private fun ReactionPills(
    reactions: List<MessageReaction>,
    reactedByMe: Set<String>,
    onToggleReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        reactions.forEach { reaction ->
            Surface(
                onClick = { onToggleReaction(reaction.emoji) },
                shape = RoundedCornerShape(18.dp),
                color = if (reaction.emoji in reactedByMe) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(reaction.emoji, fontSize = 17.sp)
                    Text(reaction.count.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(preview: LinkPreview) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).combinedClickable(
            role = Role.Button,
            onClick = { runCatching { uriHandler.openUri(preview.url) } },
            onLongClick = {}
        ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(
                    preview.host.uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!preview.title.equals(preview.host, true)) Text(
                    preview.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AttachmentContent(
    attachment: ChatAttachment,
    attachmentManager: AttachmentManager,
    attachmentVersion: Long,
    imageModifier: Modifier,
    onImageClick: () -> Unit,
    animateEntrance: Boolean
) {
    if (attachment.mimeType.startsWith("image/")) {
        val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id, attachmentVersion) {
            value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 1_440) }
        }
        var revealed by remember(attachment.id) { mutableStateOf(!animateEntrance) }
        LaunchedEffect(attachment.id, animateEntrance) {
            if (animateEntrance) {
                delay(18)
                revealed = true
            }
        }
        AnimatedVisibility(
            visible = revealed,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it }
            ) + fadeIn()
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_attachment, attachment.name),
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                        .then(imageModifier)
                        .clip(RoundedCornerShape(23.dp))
                        .combinedClickable(onClick = onImageClick, onLongClick = {}),
                    contentScale = ContentScale.Crop
                )
                attachmentManager.isReceiving(attachment.id) -> AttachmentPlaceholder(attachment)
                else -> FileAttachmentCard(attachment)
            }
        }
    } else {
        FileAttachmentCard(attachment)
    }
}

@Composable
private fun FileAttachmentCard(attachment: ChatAttachment) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AttachFile, null)
            Column(Modifier.weight(1f)) {
                Text(attachment.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (attachment.size >= 0) Text(
                    Formatter.formatShortFileSize(context, attachment.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttachmentPlaceholder(attachment: ChatAttachment) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(23.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(8.dp))
            Text(attachment.name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MessageMeta(time: String, status: String, isMine: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (isMine) {
            Spacer(Modifier.width(5.dp))
            ReceiptIcon(status)
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
        icon,
        null,
        Modifier.size(15.dp),
        tint = if (status == "read") MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun receiptStatus(status: String): String = when (status) {
    "read" -> stringResource(R.string.receipt_status_read)
    "delivered" -> stringResource(R.string.receipt_status_delivered)
    "sent" -> stringResource(R.string.receipt_status_sent)
    else -> stringResource(R.string.receipt_status_waiting)
}

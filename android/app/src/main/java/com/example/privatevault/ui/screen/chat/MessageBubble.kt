@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.privatevault.ui.screen.chat

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privatevault.R
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import com.example.privatevault.model.MessageReaction
import com.example.privatevault.model.canonicalAttachments
import com.example.privatevault.ui.theme.ChatExpressiveTokens
import com.example.privatevault.ui.theme.LocalChatBubbleColors
import com.example.privatevault.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val ReactionChoices = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    showSenderName: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    reactedByMe: Set<String>,
    selected: Boolean,
    selectionCount: Int,
    selectionMode: Boolean,
    showContextMenu: Boolean,
    replyMessage: Message?,
    replyMessageIsMine: Boolean,
    canEdit: Boolean,
    highlighted: Boolean,
    onToggleReaction: (String) -> Unit,
    onToggleSelection: () -> Unit,
    onSelect: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReplyQuoteClick: (String) -> Unit,
    onImageClick: (ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    attachmentManager: AttachmentManager,
    playExpressiveOnAppear: Boolean,
    expressiveMotionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var swipeOffset by remember(message.id) { mutableFloatStateOf(0f) }
    var settleJob by remember(message.id) { mutableStateOf<Job?>(null) }
    val swipeLimit = with(density) { 72.dp.toPx() }
    val swipeThreshold = with(density) { 56.dp.toPx() }
    var thresholdHapticSent by remember(message.id) { mutableStateOf(false) }
    var menuAbove by remember(message.id) { mutableStateOf(false) }
    var detailsVisible by remember(message.id) { mutableStateOf(false) }
    val swipeEnabled = !selectionMode && message.deletedAt == null
    val sentTime = remember(message.timestamp) { TimeUtils.display(message.timestamp) }
    val readTime = remember(message.readAt) { message.readAt?.let(TimeUtils::display) }
    val sender = if (isMine) stringResource(R.string.message_from_you)
    else stringResource(R.string.message_from_contact, stringResource(R.string.contact_name))
    val attachments = message.canonicalAttachments
    val replayableExpressiveMessage = remember(
        message.emphasisLevel,
        message.text,
        message.deletedAt,
        attachments
    ) {
        MessageEmphasis.fromStored(message.emphasisLevel) == MessageEmphasis.Maximum &&
            message.deletedAt == null &&
            attachments.isEmpty() &&
            singleEmojiOrNull(message.text) == null &&
            message.text.length <= 34
    }
    var expressivePlaybackRequest by remember(message.id) { mutableIntStateOf(0) }
    var entrancePlaybackConsumed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(playExpressiveOnAppear, expressiveMotionEnabled, replayableExpressiveMessage) {
        if (
            playExpressiveOnAppear &&
            expressiveMotionEnabled &&
            replayableExpressiveMessage &&
            !entrancePlaybackConsumed
        ) {
            entrancePlaybackConsumed = true
            expressivePlaybackRequest += 1
        }
    }
    val replayEffectLabel = if (replayableExpressiveMessage && expressiveMotionEnabled) {
        stringResource(R.string.replay_message_effect)
    } else null
    val attachmentDescription = when {
        attachments.isEmpty() -> ""
        attachments.size == 1 -> attachments.single().let {
            if (it.mimeType.startsWith("image/")) stringResource(R.string.image_attachment, it.name)
            else stringResource(R.string.file_attachment, it.name)
        }
        attachments.all { it.mimeType.startsWith("image/") } ->
            stringResource(R.string.photo_count, attachments.size)
        else -> stringResource(R.string.attachment_count, attachments.size)
    }
    val visibleContent = if (message.deletedAt != null) stringResource(R.string.message_deleted)
    else listOf(message.text, attachmentDescription).filter(String::isNotBlank).joinToString(". ")
    val accessibilitySummary = stringResource(
        R.string.message_accessibility,
        sender,
        visibleContent,
        sentTime,
        if (isMine) receiptStatus(message.status) else ""
    )
    val popupAnchorModifier = if (showContextMenu) {
        Modifier.onGloballyPositioned { coordinates ->
            menuAbove = coordinates.boundsInWindow().center.y > view.height / 2f
        }
    } else Modifier
    val messageInteractionSource = remember(message.id) { MutableInteractionSource() }
    val messageIndication = LocalIndication.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (groupedWithPrevious) 2.dp else 14.dp)
            .testTag("message-${message.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilitySummary
                this.selected = selected
            },
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (showSenderName && !isMine) {
            Text(
                stringResource(R.string.contact_name),
                Modifier.padding(start = 4.dp, bottom = 7.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (message.senderDeviceId == MessageStore.VIEWER_DEVICE_ID) {
            PrivateMessageTag(Modifier.padding(start = 4.dp, bottom = 5.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = popupAnchorModifier
                    .draggable(
                        enabled = swipeEnabled,
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (delta <= 0f && swipeOffset <= 0f) return@rememberDraggableState
                            settleJob?.cancel()
                            val next = (swipeOffset + delta).coerceAtLeast(0f)
                            val resisted = if (next <= swipeLimit) next else swipeLimit + (next - swipeLimit) * 0.12f
                            swipeOffset = resisted.coerceAtMost(swipeLimit + with(density) { 8.dp.toPx() })
                            if (next >= swipeThreshold && !thresholdHapticSent) {
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                thresholdHapticSent = true
                            } else if (next < swipeThreshold * 0.72f) thresholdHapticSent = false
                        },
                        onDragStopped = { velocity ->
                            val shouldReply = swipeOffset >= swipeThreshold || velocity > 1_250f
                            if (shouldReply) onReply()
                            thresholdHapticSent = false
                            val startOffset = swipeOffset
                            settleJob = scope.launch {
                                Animatable(startOffset).animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) { swipeOffset = value }
                            }
                        }
                    )
                    .graphicsLayer { translationX = swipeOffset }
            ) {
                Box(
                    Modifier
                        .clip(bubbleShape(isMine, groupedWithPrevious, groupedWithNext))
                        .combinedClickable(
                        interactionSource = messageInteractionSource,
                        indication = if (replayableExpressiveMessage) null else messageIndication,
                        role = Role.Button,
                        onClickLabel = replayEffectLabel,
                        onClick = {
                            if (selectionMode) onToggleSelection()
                            else if (replayableExpressiveMessage && expressiveMotionEnabled) {
                                expressivePlaybackRequest += 1
                            }
                            else if (message.status == "read") detailsVisible = !detailsVisible
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect()
                        }
                        )
                ) {
                    MessageSurface(
                        message = message,
                        isMine = isMine,
                        groupedWithPrevious = groupedWithPrevious,
                        groupedWithNext = groupedWithNext,
                        selected = selected,
                        highlighted = highlighted,
                        replyMessage = replyMessage,
                        replyMessageIsMine = replyMessageIsMine,
                        sentTime = sentTime,
                        readTime = readTime,
                        showDetails = detailsVisible,
                        onReplyQuoteClick = onReplyQuoteClick,
                        onImageClick = onImageClick,
                        imageModifier = imageModifier,
                        attachmentManager = attachmentManager,
                        expressivePlaybackRequest = expressivePlaybackRequest,
                        expressiveMotionEnabled = expressiveMotionEnabled
                    )
                }
                if (showContextMenu) {
                    MessageContextMenu(
                        expanded = true,
                        selectionCount = selectionCount,
                        menuAbove = menuAbove,
                        canAct = message.deletedAt == null,
                        canEdit = canEdit,
                        onDismiss = onDismissContextMenu,
                        onReaction = onToggleReaction,
                        onReply = onReply,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
            }
        }
        if (message.deletedAt == null && message.reactions.isNotEmpty()) {
            ReactionPills(
                message.reactions,
                reactedByMe,
                onToggleReaction,
                Modifier.padding(start = if (isMine) 0.dp else 8.dp, end = if (isMine) 8.dp else 0.dp)
                    .offset(y = (-5).dp)
            )
        }
    }
}

@Composable
private fun PrivateMessageTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = "Private",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MessageSurface(
    message: Message,
    isMine: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    replyMessage: Message?,
    replyMessageIsMine: Boolean,
    sentTime: String,
    readTime: String?,
    showDetails: Boolean,
    onReplyQuoteClick: (String) -> Unit,
    onImageClick: (ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    attachmentManager: AttachmentManager,
    expressivePlaybackRequest: Int,
    expressiveMotionEnabled: Boolean
) {
    val emphasis = MessageEmphasis.fromStored(message.emphasisLevel)
    val attachments = message.canonicalAttachments
    val emoji = remember(message.text, attachments, message.deletedAt) {
        if (attachments.isEmpty() && message.deletedAt == null) singleEmojiOrNull(message.text) else null
    }
    when {
        message.deletedAt != null -> DeletedMessage(
            message,
            isMine,
            groupedWithPrevious,
            groupedWithNext,
            selected,
            highlighted
        )
        emoji != null -> EmojiOnlyMessage(
            emoji,
            sentTime,
            readTime,
            message.status,
            isMine,
            emphasis,
            message.editedAt != null,
            selected,
            highlighted,
            showDetails
        )
        emphasis == MessageEmphasis.Maximum && attachments.isEmpty() && message.text.length <= 34 ->
            MaximumExpressiveMessage(
                message.text,
                sentTime,
                readTime,
                message.status,
                isMine,
                message.editedAt != null,
                selected,
                highlighted,
                showDetails,
                expressivePlaybackRequest,
                expressiveMotionEnabled
            )
        else -> ConventionalBubble(
            message,
            isMine,
            sentTime,
            readTime,
            emphasis,
            groupedWithPrevious,
            groupedWithNext,
            selected,
            highlighted,
            replyMessage,
            replyMessageIsMine,
            showDetails,
            onReplyQuoteClick,
            onImageClick,
            imageModifier,
            attachmentManager
        )
    }
}

@Composable
private fun ConventionalBubble(
    message: Message,
    isMine: Boolean,
    sentTime: String,
    readTime: String?,
    emphasis: MessageEmphasis,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    replyMessage: Message?,
    replyMessageIsMine: Boolean,
    showDetails: Boolean,
    onReplyQuoteClick: (String) -> Unit,
    onImageClick: (ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    attachmentManager: AttachmentManager
) {
    BoxWithConstraints {
        val bubbleColors = LocalChatBubbleColors.current
        val metrics = messageVisualMetrics(
            message.text,
            emphasis.progress,
            MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp)
        )
        val selectedColor = MaterialTheme.colorScheme.tertiaryContainer
        val attachments = message.canonicalAttachments
        Surface(
            modifier = Modifier.widthIn(max = maxWidth * ChatExpressiveTokens.MessageMaxWidthFraction),
            color = when {
                selected || highlighted -> selectedColor
                isMine -> bubbleColors.outgoing
                else -> bubbleColors.incoming
            },
            contentColor = if (selected || highlighted) MaterialTheme.colorScheme.onTertiaryContainer
            else if (isMine) bubbleColors.onOutgoing
            else bubbleColors.onIncoming,
            shape = bubbleShape(isMine, groupedWithPrevious, groupedWithNext),
            tonalElevation = if (isMine) 1.dp else 0.dp
        ) {
            Column(
                Modifier.padding(
                    horizontal = if (attachments.isNotEmpty()) 5.dp else metrics.horizontalPadding,
                    vertical = if (attachments.isNotEmpty()) 5.dp else metrics.verticalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                message.replyToMessageId?.let { targetId ->
                    ReplyQuote(replyMessage, replyMessageIsMine, targetId, onReplyQuoteClick)
                }
                if (attachments.isNotEmpty()) {
                    MessageAttachments(
                        attachments = attachments,
                        attachmentManager = attachmentManager,
                        imageModifier = imageModifier,
                        onImageClick = onImageClick
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(
                        message.text,
                        Modifier.padding(
                            horizontal = if (attachments.isNotEmpty()) 10.dp else 0.dp,
                            vertical = if (attachments.isNotEmpty()) 3.dp else 0.dp
                        ),
                        style = metrics.textStyle
                    )
                }
                remember(message.text) { LinkPreviewLoader.firstUrl(message.text) }?.let { url ->
                    val preview by produceState(LinkPreviewLoader.fallback(url), url) {
                        value = LinkPreviewLoader.load(url)
                    }
                    LinkPreviewCard(preview)
                }
                MessageMeta(
                    sentTime = sentTime,
                    readTime = readTime,
                    status = message.status,
                    isMine = isMine,
                    edited = message.editedAt != null,
                    showDetails = showDetails,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = if (attachments.isNotEmpty()) 7.dp else 0.dp)
                )
            }
        }
    }
}

@Composable
private fun ReplyQuote(message: Message?, messageIsMine: Boolean, targetId: String, onClick: (String) -> Unit) {
    val unavailable = message == null
    val deleted = message?.deletedAt != null
    val summary = when {
        unavailable -> stringResource(R.string.original_message_unavailable)
        deleted -> stringResource(R.string.message_deleted)
        message.text.isNotBlank() -> message.text
        else -> {
            val attachments = message.canonicalAttachments
            when {
                attachments.size == 1 -> attachments.single().name
                attachments.isNotEmpty() && attachments.all { it.mimeType.startsWith("image/") } ->
                    stringResource(R.string.photo_count, attachments.size)
                attachments.isNotEmpty() -> stringResource(R.string.attachment_count, attachments.size)
                else -> stringResource(R.string.original_message_unavailable)
            }
        }
    }
    Surface(
        onClick = { onClick(targetId) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                if (message == null) stringResource(R.string.original_message)
                else if (messageIsMine) stringResource(R.string.message_from_you)
                else stringResource(R.string.contact_name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DeletedMessage(
    message: Message,
    isMine: Boolean,
    groupedWithPrevious: Boolean,
    groupedWithNext: Boolean,
    selected: Boolean,
    highlighted: Boolean
) {
    Surface(
        shape = bubbleShape(isMine, groupedWithPrevious, groupedWithNext),
        color = if (selected || highlighted) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Delete, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.message_deleted),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmojiOnlyMessage(
    emoji: String,
    sentTime: String,
    readTime: String?,
    status: String,
    isMine: Boolean,
    emphasis: MessageEmphasis,
    edited: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    showDetails: Boolean
) {
    Surface(
        color = if (selected || highlighted) MaterialTheme.colorScheme.tertiaryContainer
        else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = null
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(emoji, fontSize = if (emphasis == MessageEmphasis.Maximum) 72.sp else 64.sp, lineHeight = 76.sp)
            MessageMeta(sentTime, readTime, status, isMine, edited, showDetails)
        }
    }
}

@Composable
private fun MaximumExpressiveMessage(
    text: String,
    sentTime: String,
    readTime: String?,
    status: String,
    isMine: Boolean,
    edited: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    showDetails: Boolean,
    playbackRequest: Int,
    motionEnabled: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val chatColors = LocalChatBubbleColors.current
    val shimmerProgress = remember { Animatable(0f) }
    val twinkleProgress = remember { Animatable(0f) }
    val waveProgress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    var wavePlaying by remember { mutableStateOf(false) }
    LaunchedEffect(playbackRequest, motionEnabled) {
        if (playbackRequest <= 0 || !motionEnabled) {
            playing = false
            wavePlaying = false
            return@LaunchedEffect
        }
        if (playbackRequest == 1) delay(140)
        playing = true
        try {
            shimmerProgress.snapTo(-0.35f)
            twinkleProgress.snapTo(0f)
            waveProgress.snapTo(0f)
            coroutineScope {
                launch {
                    shimmerProgress.animateTo(
                        targetValue = 1.35f,
                        animationSpec = tween(durationMillis = 820, easing = LinearEasing)
                    )
                }
                launch {
                    twinkleProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 820, easing = LinearEasing)
                    )
                }
                launch {
                    wavePlaying = true
                    try {
                        waveProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 290, easing = FastOutSlowInEasing)
                        )
                    } finally {
                        wavePlaying = false
                    }
                }
            }
        } finally {
            playing = false
        }
    }
    val shimmerBrush = if (playing) {
        val center = shimmerProgress.value * 520f
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to chatColors.expressiveStart,
                0.38f to chatColors.expressiveEnd,
                0.5f to Color.White.copy(alpha = 0.96f),
                0.62f to chatColors.expressiveEnd,
                1f to chatColors.expressiveStart
            ),
            start = Offset(center - 150f, -40f),
            end = Offset(center + 150f, 190f)
        )
    } else {
        Brush.linearGradient(listOf(chatColors.expressiveStart, chatColors.expressiveEnd))
    }
    val firstTwinkle = twinklePulse(twinkleProgress.value, delay = 0f)
    val secondTwinkle = twinklePulse(twinkleProgress.value, delay = 0.28f)
    Surface(
        color = if (selected || highlighted) colors.tertiaryContainer
        else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = null
    ) {
        Column(
            Modifier.widthIn(max = 350.dp).padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    "✦",
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-14).dp)
                        .graphicsLayer {
                            alpha = 0.72f + firstTwinkle * 0.28f
                            val scale = 0.92f + firstTwinkle * 0.16f
                            scaleX = scale
                            scaleY = scale
                            rotationZ = firstTwinkle * 7f
                    },
                    fontSize = 24.sp,
                    color = chatColors.expressiveEnd
                )
                val expressiveTextStyle = TextStyle(
                        brush = shimmerBrush,
                        fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = when { text.length <= 8 -> 68.sp; text.length <= 18 -> 54.sp; else -> 42.sp },
                        lineHeight = when { text.length <= 8 -> 72.sp; text.length <= 18 -> 58.sp; else -> 47.sp },
                        shadow = Shadow(chatColors.expressiveStart.copy(alpha = 0.22f), blurRadius = 18f)
                    )
                val waveTextStyle = expressiveTextStyle.copy(
                    brush = Brush.linearGradient(
                        listOf(chatColors.expressiveStart, chatColors.expressiveEnd)
                    )
                )
                var textLayoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
                val waveOverlayReady = wavePlaying && textLayoutResult != null
                Box {
                    Text(
                        text,
                        modifier = Modifier.graphicsLayer { alpha = if (waveOverlayReady) 0f else 1f },
                        style = expressiveTextStyle,
                        onTextLayout = { result ->
                            if (textLayoutResult == null) textLayoutResult = result
                        }
                    )
                    if (waveOverlayReady) {
                        StaggeredScaleText(
                            text = text,
                            style = waveTextStyle,
                            textLayoutResult = textLayoutResult!!,
                            progress = { waveProgress.value },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Text(
                    "✦",
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-13).dp, y = 12.dp)
                        .graphicsLayer {
                            alpha = 0.58f + secondTwinkle * 0.3f
                            val scale = 0.9f + secondTwinkle * 0.17f
                            scaleX = scale
                            scaleY = scale
                            rotationZ = secondTwinkle * -8f
                    },
                    fontSize = 18.sp,
                    color = chatColors.expressiveStart
                )
            }
            MessageMeta(sentTime, readTime, status, isMine, edited, showDetails)
        }
    }
}

@Composable
private fun StaggeredScaleText(
    text: String,
    style: TextStyle,
    textLayoutResult: TextLayoutResult,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    val graphemes = remember(text) { expressiveGraphemes(text) }
    val graphemeStarts = remember(graphemes) {
        buildList {
            var offset = 0
            graphemes.forEach { grapheme ->
                add(offset)
                offset += grapheme.length
            }
        }
    }
    Layout(
        content = {
            graphemes.forEachIndexed { index, grapheme ->
                if (grapheme == "\n") {
                    Spacer(Modifier.size(0.dp))
                } else {
                    Text(
                        text = grapheme,
                        maxLines = 1,
                        style = style,
                        modifier = Modifier
                            .graphicsLayer {
                                val scale = letterWaveScale(progress(), index, graphemes.size)
                                scaleX = scale
                                scaleY = scale
                            }
                            .clearAndSetSemantics { }
                    )
                }
            }
        },
        modifier = modifier.clearAndSetSemantics { }
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(looseConstraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                if (graphemes[index] != "\n") {
                    val characterOffset = graphemeStarts[index].coerceIn(0, text.lastIndex)
                    val line = textLayoutResult.getLineForOffset(characterOffset)
                    val x = textLayoutResult
                        .getHorizontalPosition(characterOffset, usePrimaryDirection = true)
                        .roundToInt()
                    val childBaseline = placeable[FirstBaseline]
                        .takeUnless { it == AlignmentLine.Unspecified }
                        ?: 0
                    val y = textLayoutResult.getLineBaseline(line).roundToInt() - childBaseline
                    placeable.placeWithLayer(x, y) {
                        val scale = letterWaveScale(progress(), index, graphemes.size)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                        clip = false
                    }
                }
            }
        }
    }
}

private fun twinklePulse(progress: Float, delay: Float): Float {
    if (progress <= delay || progress >= 1f) return 0f
    val localProgress = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
    return sin(localProgress * PI).toFloat().coerceAtLeast(0f)
}

@Composable
private fun MessageContextMenu(
    expanded: Boolean,
    selectionCount: Int,
    menuAbove: Boolean,
    canAct: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        if (selectionCount > 1) {
            ActionRow(false, false, onReply, onEdit, onDelete)
        } else {
            if (menuAbove) ReactionRow(canAct, onReaction)
            ActionRow(canAct, canEdit, onReply, onEdit, onDelete)
            if (!menuAbove) ReactionRow(canAct, onReaction)
        }
    }
}

@Composable
private fun ReactionRow(enabled: Boolean, onReaction: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) {
        ReactionChoices.forEach { emoji ->
            val description = stringResource(R.string.insert_emoji, emoji)
            IconButton(
                enabled = enabled,
                onClick = { onReaction(emoji) },
                modifier = Modifier.size(44.dp).semantics { contentDescription = description }
            ) { Text(emoji, fontSize = 22.sp) }
        }
    }
}

@Composable
private fun ActionRow(
    canReply: Boolean,
    canEdit: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        if (canReply) ContextAction(Icons.AutoMirrored.Filled.Reply, stringResource(R.string.reply), onReply)
        if (canEdit) ContextAction(Icons.Default.Edit, stringResource(R.string.edit), onEdit)
        ContextAction(Icons.Default.Delete, stringResource(R.string.delete), onDelete)
    }
}

@Composable
private fun ContextAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun bubbleShape(isMine: Boolean, previous: Boolean, next: Boolean): RoundedCornerShape {
    val outer = 24.dp
    val joined = 4.dp
    return if (isMine) {
        RoundedCornerShape(
            topStart = outer,
            topEnd = if (previous) joined else outer,
            bottomEnd = if (next) joined else outer,
            bottomStart = outer
        )
    } else {
        RoundedCornerShape(
            topStart = if (previous) joined else outer,
            topEnd = outer,
            bottomEnd = outer,
            bottomStart = if (next) joined else outer
        )
    }
}

@Composable
private fun ReactionPills(reactions: List<MessageReaction>, reactedByMe: Set<String>, onToggleReaction: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        reactions.forEach { reaction ->
            Surface(
                onClick = { onToggleReaction(reaction.emoji) },
                shape = RoundedCornerShape(18.dp),
                color = if (reaction.emoji in reactedByMe) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(role = Role.Button) {
            runCatching { uriHandler.openUri(preview.url) }
        },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(preview.host.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                if (!preview.title.equals(preview.host, true)) Text(preview.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MessageAttachments(
    attachments: List<ChatAttachment>,
    attachmentManager: AttachmentManager,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    onImageClick: (ChatAttachment) -> Unit
) {
    val images = remember(attachments) { attachments.filter { it.mimeType.startsWith("image/") } }
    val files = remember(attachments) { attachments.filterNot { it.mimeType.startsWith("image/") } }
    when (images.size) {
        0 -> Unit
        1 -> ImageAttachmentContent(
            attachment = images.single(),
            attachmentManager = attachmentManager,
            imageModifier = imageModifier(images.single()),
            onImageClick = { onImageClick(images.single()) }
        )
        else -> {
            val carouselState = rememberCarouselState { images.size }
            val preferredItemWidth = remember(images) {
                val knownAspects = images.mapNotNull { attachment ->
                    val width = attachment.width ?: return@mapNotNull null
                    val height = attachment.height ?: return@mapNotNull null
                    if (width > 0 && height > 0) width.toDouble() / height else null
                }
                val representativeAspect = knownAspects.takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.coerceIn(0.5, 1.0)
                    ?: 0.75
                (240.0 * representativeAspect).coerceIn(120.0, 180.0).dp
            }
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                preferredItemWidth = preferredItemWidth,
                itemSpacing = 8.dp,
                flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carouselState)
            ) { index ->
                val attachment = images[index]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(RoundedCornerShape(23.dp))
                        .clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    ImageAttachmentContent(
                        attachment = attachment,
                        attachmentManager = attachmentManager,
                        imageModifier = imageModifier(attachment),
                        onImageClick = { onImageClick(attachment) },
                        fillCarouselItem = true,
                        position = index + 1,
                        total = images.size
                    )
                }
            }
        }
    }
    files.forEach { FileAttachmentCard(it) }
}

@Composable
private fun ImageAttachmentContent(
    attachment: ChatAttachment,
    attachmentManager: AttachmentManager,
    imageModifier: Modifier,
    onImageClick: () -> Unit,
    fillCarouselItem: Boolean = false,
    position: Int = 1,
    total: Int = 1
) {
    val revision by remember(attachment.id) { attachmentManager.revision(attachment.id) }.collectAsStateWithLifecycle()
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id, revision) {
        value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 960) }
    }
    val alpha by animateFloatAsState(if (bitmap == null) 0f else 1f, tween(140), label = "attachment-alpha")
    val scale by animateFloatAsState(if (bitmap == null) 0.98f else 1f, tween(180), label = "attachment-scale")
    val imageAspect = remember(bitmap, attachment.width, attachment.height) {
        when {
            bitmap != null -> bitmap!!.width.toFloat() / bitmap!!.height.coerceAtLeast(1)
            attachment.width != null && attachment.height != null ->
                attachment.width.toFloat() / attachment.height.coerceAtLeast(1)
            else -> 4f / 3f
        }.coerceIn(0.35f, 3f)
    }
    BoxWithConstraints(if (fillCarouselItem) Modifier.fillMaxSize() else Modifier) {
        val availableHeight = if (fillCarouselItem) maxHeight else 240.dp
        val imageWidth = if (fillCarouselItem) maxWidth else minOf(maxWidth, availableHeight * imageAspect)
        val imageHeight = if (fillCarouselItem) maxHeight else minOf(availableHeight, imageWidth / imageAspect)
        Box(
            Modifier
                .size(width = imageWidth, height = imageHeight)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(23.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> Image(
                    bitmap!!.asImageBitmap(),
                    if (total > 1) stringResource(R.string.image_position, position, total)
                    else stringResource(R.string.image_attachment, attachment.name),
                    Modifier.fillMaxSize().then(imageModifier).graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                    }.clickable(onClick = onImageClick),
                    contentScale = ContentScale.Fit
                )
                attachmentManager.isReceiving(attachment.id) -> AttachmentPlaceholder(attachment)
                else -> FileAttachmentCard(attachment)
            }
        }
    }
}

@Composable
private fun FileAttachmentCard(attachment: ChatAttachment) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, null)
            Column(Modifier.weight(1f)) {
                Text(attachment.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (attachment.size >= 0) Text(Formatter.formatShortFileSize(context, attachment.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AttachmentPlaceholder(attachment: ChatAttachment) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(8.dp))
        Text(attachment.name, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MessageMeta(
    sentTime: String,
    readTime: String?,
    status: String,
    isMine: Boolean,
    edited: Boolean,
    showDetails: Boolean,
    modifier: Modifier = Modifier
) {
    val isRead = status == "read"
    if (isRead && !showDetails && !edited) return
    val metadataColor = LocalContentColor.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (edited) {
            Text(stringResource(R.string.edited), style = MaterialTheme.typography.labelSmall, color = metadataColor)
            if (showDetails || !isRead) Spacer(Modifier.width(5.dp))
        }
        if (isRead && showDetails) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.message_sent_at, sentTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = metadataColor,
                    maxLines = 1
                )
                readTime?.let {
                    Text(
                        stringResource(R.string.message_read_at, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = metadataColor,
                        maxLines = 1
                    )
                }
            }
        } else if (!isRead) {
            Text(sentTime, style = MaterialTheme.typography.labelSmall, color = metadataColor, maxLines = 1)
            if (isMine) {
                Spacer(Modifier.width(5.dp))
                ReceiptIcon(status, metadataColor)
            }
        }
    }
}

@Composable
private fun ReceiptIcon(status: String, tint: Color) {
    val icon = if (status == "sent" || status == "delivered") Icons.Default.Done else Icons.Default.Schedule
    Icon(icon, null, Modifier.size(15.dp), tint = tint)
}

@Composable
private fun receiptStatus(status: String): String = when (status) {
    "read" -> stringResource(R.string.receipt_status_read)
    "delivered" -> stringResource(R.string.receipt_status_delivered)
    "sent" -> stringResource(R.string.receipt_status_sent)
    else -> stringResource(R.string.receipt_status_waiting)
}

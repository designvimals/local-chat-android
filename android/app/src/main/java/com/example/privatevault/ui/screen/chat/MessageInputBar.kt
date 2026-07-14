package com.example.privatevault.ui.screen.chat

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.privatevault.R
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.model.MessageEmphasis
import com.example.privatevault.model.canonicalAttachments
import com.example.privatevault.ui.theme.ChatExpressiveTokens
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val QuickEmojis = listOf(
    "😀", "😂", "🥹", "😍", "🥰", "😎", "🤔", "😭",
    "👍", "👎", "👏", "🙏", "👌", "✌️", "🤝", "💪",
    "❤️", "🔥", "✨", "🎉", "💯", "✅", "👀", "🤗"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageInputBar(
    onSend: suspend (String, Int, String?) -> Result<Message>,
    onEdit: suspend (String, String) -> Result<Message>,
    onAttachFiles: () -> Unit,
    pendingAttachments: List<ChatAttachment>,
    onRemovePendingAttachment: (String) -> Unit,
    onSendAttachments: suspend (List<ChatAttachment>, String, String?) -> Result<Message>,
    attachmentManager: AttachmentManager,
    onTextChanged: (String) -> Unit,
    replyMessage: Message?,
    editingMessage: Message?,
    onClearContext: () -> Unit,
    onSent: (Message) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var emojiPanelVisible by rememberSaveable { mutableStateOf(false) }
    var draftBeforeEdit by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var maximumHapticSent by remember { mutableStateOf(false) }
    val trimmed = text.trim()
    val canSend = !sending && (trimmed.isNotBlank() || (pendingAttachments.isNotEmpty() && editingMessage == null))
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val stateMachine = remember { SendEmphasisStateMachine() }
    var gestureState by remember { mutableStateOf(stateMachine.state) }
    val previewPop = remember { Animatable(1f) }
    val previewShake = remember { Animatable(0f) }
    val buttonPop = remember { Animatable(1f) }
    var sendBurstNonce by remember { mutableIntStateOf(0) }
    var cancelResetJob by remember { mutableStateOf<Job?>(null) }

    fun publish(state: SendEmphasisUiState) { gestureState = state }
    fun updateText(value: String) {
        text = value
        onTextChanged(value)
    }
    fun resetGesture() {
        maximumHapticSent = false
        publish(stateMachine.reset())
    }
    fun cancelGesture(withHaptic: Boolean = true) {
        if (stateMachine.state.phase == SendInteractionPhase.Idle ||
            stateMachine.state.phase == SendInteractionPhase.Cancelling
        ) return
        publish(stateMachine.cancel())
        if (withHaptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        cancelResetJob?.cancel()
        cancelResetJob = scope.launch {
            if (animationsEnabled) delay(ChatExpressiveTokens.CancelSettleMillis)
            resetGesture()
        }
    }
    fun clearComposerContext(restoreDraft: Boolean) {
        val previousDraft = draftBeforeEdit
        draftBeforeEdit = null
        if (restoreDraft && previousDraft != null) updateText(previousDraft)
        onClearContext()
    }
    fun commit(emphasis: MessageEmphasis = MessageEmphasis.Normal) {
        if (!canSend) return
        cancelResetJob?.cancel()
        sending = true
        val edit = editingMessage
        scope.launch {
            val result: Result<Message> = when {
                edit != null -> onEdit(edit.id, trimmed)
                pendingAttachments.isNotEmpty() -> onSendAttachments(
                    pendingAttachments,
                    trimmed,
                    replyMessage?.id
                )
                else -> onSend(trimmed, emphasis.storedValue, replyMessage?.id)
            }
            sending = false
            result.onSuccess { sentMessage ->
                if (animationsEnabled && edit == null) sendBurstNonce += 1
                if (edit != null) clearComposerContext(restoreDraft = true)
                else {
                    updateText("")
                    clearComposerContext(restoreDraft = false)
                }
                emojiPanelVisible = false
                resetGesture()
                if (edit == null) onSent(sentMessage)
            }
        }
    }

    LaunchedEffect(editingMessage?.id) {
        val edit = editingMessage ?: return@LaunchedEffect
        if (draftBeforeEdit == null) draftBeforeEdit = text
        updateText(edit.text)
        emojiPanelVisible = false
        focusRequester.requestFocus()
        keyboard?.show()
    }
    fun toggleEmojiPanel() {
        cancelGesture()
        emojiPanelVisible = !emojiPanelVisible
        if (emojiPanelVisible) {
            focusManager.clearFocus()
            keyboard?.hide()
        } else {
            scope.launch {
                androidx.compose.runtime.withFrameNanos { }
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { onTextChanged("") } }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) cancelGesture(withHaptic = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(trimmed, pendingAttachments) {
        if (trimmed.isBlank() && gestureState.phase != SendInteractionPhase.Idle) {
            cancelGesture(withHaptic = false)
        }
    }
    LaunchedEffect(gestureState.popCount) {
        if (gestureState.popCount == 0) return@LaunchedEffect
        if (!maximumHapticSent) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            maximumHapticSent = true
        }
        if (!animationsEnabled) return@LaunchedEffect
        coroutineScope {
            launch {
                previewPop.snapTo(1f)
                previewPop.animateTo(ChatExpressiveTokens.PopPreviewScale, spring(
                    dampingRatio = ChatExpressiveTokens.PopSpringDamping,
                    stiffness = ChatExpressiveTokens.PopSpringStiffness
                ))
                previewPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
            }
            launch {
                buttonPop.snapTo(1f)
                buttonPop.animateTo(ChatExpressiveTokens.PopButtonScale, spring(
                    dampingRatio = ChatExpressiveTokens.PopSpringDamping,
                    stiffness = ChatExpressiveTokens.PopSpringStiffness
                ))
                buttonPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
            }
        }
    }
    LaunchedEffect(gestureState.phase, animationsEnabled) {
        previewShake.snapTo(0f)
        if (!animationsEnabled || gestureState.phase != SendInteractionPhase.AtMaximum) return@LaunchedEffect
        while (isActive && gestureState.phase == SendInteractionPhase.AtMaximum) {
            previewShake.animateTo(-2.2f, tween(48))
            previewShake.animateTo(2.2f, tween(48))
        }
        previewShake.snapTo(0f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    0.38f to MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.82f),
                    1f to MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(8.dp)
    ) {
        val dragRangePx = with(density) {
            max(ChatExpressiveTokens.MinimumDragRange.toPx(), maxWidth.toPx() * 0.62f)
        }
        val verticalCancelPx = with(density) { ChatExpressiveTokens.VerticalCancelDistance.toPx() }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(38.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Column(
                Modifier.fillMaxWidth()
            ) {
                AnimatedVisibility(
                    visible = gestureState.showsPreview,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    EmphasisPreview(
                        gestureState,
                        previewPop.asState(),
                        previewShake.asState(),
                        animationsEnabled
                    )
                }
                AnimatedVisibility(
                    visible = pendingAttachments.isNotEmpty(),
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    PendingAttachmentPreviews(
                        pendingAttachments,
                        attachmentManager,
                        onRemovePendingAttachment
                    )
                }
                if (replyMessage != null || editingMessage != null) {
                    ComposerContextBanner(
                        message = editingMessage ?: replyMessage!!,
                        editing = editingMessage != null,
                        onClose = { clearComposerContext(restoreDraft = editingMessage != null) }
                    )
                }
                if (emojiPanelVisible) EmojiPanel { emoji -> updateText(text + emoji) }
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    ComposerActionButton(
                        icon = Icons.Default.Add,
                        description = stringResource(R.string.attach_file),
                        onClick = { cancelGesture(); onAttachFiles() }
                    )
                    TextField(
                        value = text,
                        onValueChange = ::updateText,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 112.dp)
                            .testTag("message-composer-input")
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) emojiPanelVisible = false },
                        shape = if (text.isBlank()) CircleShape else RoundedCornerShape(24.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
                        placeholder = {
                            Text(
                                stringResource(R.string.message_placeholder),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = ::toggleEmojiPanel) {
                                Icon(
                                    Icons.Outlined.EmojiEmotions,
                                    contentDescription = stringResource(
                                        if (emojiPanelVisible) R.string.close_emoji_picker else R.string.open_emoji_picker
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { commit() }),
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    AnimatedVisibility(
                        visible = canSend,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        ExpressiveSendControl(
                            enabled = canSend,
                            holdEnabled = pendingAttachments.isEmpty() && trimmed.isNotBlank(),
                            message = trimmed,
                            stateMachine = stateMachine,
                            state = gestureState,
                            publish = ::publish,
                            onEnterHold = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onCancel = { cancelGesture() },
                            onQuickSend = { commit() },
                            onEmphasizedSend = { emphasis -> commit(emphasis) },
                            dragRangePx = dragRangePx,
                            verticalCancelPx = verticalCancelPx,
                            touchSlopPx = viewConfiguration.touchSlop,
                            popScale = buttonPop.asState()
                        )
                    }
                    }
                    Box(Modifier.matchParentSize()) {
                        SendStarBurst(
                            event = sendBurstNonce,
                            enabled = animationsEnabled,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(78.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun ComposerContextBanner(message: Message, editing: Boolean, onClose: () -> Unit) {
    val attachments = message.canonicalAttachments
    val summary = when {
        message.text.isNotBlank() -> message.text
        attachments.size == 1 -> attachments.single().name
        attachments.isNotEmpty() && attachments.all { it.mimeType.startsWith("image/") } ->
            stringResource(R.string.photo_count, attachments.size)
        attachments.isNotEmpty() -> stringResource(R.string.attachment_count, attachments.size)
        else -> stringResource(R.string.message_deleted)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 9.dp, end = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 3.dp, height = 38.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                stringResource(if (editing) R.string.editing_message else R.string.replying_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(summary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.Close,
                stringResource(if (editing) R.string.cancel_edit else R.string.cancel_reply),
                Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PendingAttachmentPreviews(
    attachments: List<ChatAttachment>,
    attachmentManager: AttachmentManager,
    onRemove: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = ChatAttachment::id) { attachment ->
            PendingAttachmentPreview(
                attachment = attachment,
                attachmentManager = attachmentManager,
                onRemove = { onRemove(attachment.id) }
            )
        }
    }
}

@Composable
private fun PendingAttachmentPreview(
    attachment: ChatAttachment,
    attachmentManager: AttachmentManager,
    onRemove: () -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id) {
        value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 960) }
    }
    Box(
        modifier = Modifier.size(width = 112.dp, height = 94.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            if (bitmap != null && attachment.mimeType.startsWith("image/")) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_attachment, attachment.name),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    attachment.name,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Surface(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
            contentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, stringResource(R.string.close), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun EmphasisPreview(
    state: SendEmphasisUiState,
    popScale: State<Float>,
    shakeOffsetDp: State<Float>,
    animationsEnabled: Boolean
) {
    val progress by animateFloatAsState(
        if (state.phase == SendInteractionPhase.Cancelling) 0f else state.progress,
        if (state.phase == SendInteractionPhase.Cancelling && animationsEnabled) spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ) else snap(),
        label = "message-emphasis-preview"
    )
    val metrics = messageVisualMetrics(
        state.messageSnapshot,
        progress,
        MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp)
    )
    val emphasis = MessageEmphasis.fromProgress(progress)
    val stateLabel = if (state.phase == SendInteractionPhase.Cancelling) {
        stringResource(R.string.send_cancelled)
    } else emphasisLabel(emphasis)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 54.dp, top = 12.dp, end = 16.dp),
        horizontalAlignment = Alignment.End
    ) {
        val previewModifier = Modifier
            .widthIn(max = 340.dp)
            .graphicsLayer {
                scaleX = if (animationsEnabled) popScale.value else 1f
                scaleY = if (animationsEnabled) popScale.value else 1f
                translationX = if (animationsEnabled) shakeOffsetDp.value.dp.toPx() else 0f
                rotationZ = if (animationsEnabled) shakeOffsetDp.value * 0.22f else 0f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
            }
            .semantics {
                contentDescription = state.messageSnapshot
                stateDescription = stateLabel
                liveRegion = LiveRegionMode.Polite
            }
        val emoji = singleEmojiOrNull(state.messageSnapshot)
        if (emoji != null) {
            Text(
                emoji,
                previewModifier.padding(horizontal = 8.dp),
                fontSize = if (emphasis == MessageEmphasis.Maximum) 72.sp else 64.sp,
                lineHeight = 76.sp
            )
        } else {
            Surface(
                modifier = previewModifier,
                shape = ChatExpressiveTokens.OutgoingBubbleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    state.messageSnapshot,
                    Modifier.padding(metrics.horizontalPadding, metrics.verticalPadding),
                    style = metrics.textStyle
                )
            }
        }
        Text(
            stringResource(R.string.drag_left_to_reduce),
            Modifier.padding(top = 5.dp, end = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ExpressiveSendControl(
    enabled: Boolean,
    holdEnabled: Boolean,
    message: String,
    stateMachine: SendEmphasisStateMachine,
    state: SendEmphasisUiState,
    publish: (SendEmphasisUiState) -> Unit,
    onEnterHold: () -> Unit,
    onCancel: () -> Unit,
    onQuickSend: () -> Unit,
    onEmphasizedSend: (MessageEmphasis) -> Unit,
    dragRangePx: Float,
    verticalCancelPx: Float,
    touchSlopPx: Float,
    popScale: State<Float>
) {
    val latestMessage by rememberUpdatedState(message)
    val latestQuickSend by rememberUpdatedState(onQuickSend)
    val latestEmphasizedSend by rememberUpdatedState(onEmphasizedSend)
    val latestCancel by rememberUpdatedState(onCancel)
    val latestEnterHold by rememberUpdatedState(onEnterHold)
    val latestPublish by rememberUpdatedState(publish)
    val sendDescription = stringResource(R.string.send_message_hint)
    val activeContainer = lerp(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        state.progress * 0.42f
    )
    val scale = when (state.phase) {
        SendInteractionPhase.Pressed -> ChatExpressiveTokens.ButtonPressedScale
        SendInteractionPhase.Holding, SendInteractionPhase.Adjusting, SendInteractionPhase.AtMaximum ->
            1f + 0.07f * state.progress
        SendInteractionPhase.Sending -> 0.96f
        else -> 1f
    }
    Surface(
        modifier = Modifier
            .size(48.dp)
            .testTag("expressive-send-button")
            .then(
                if (holdEnabled) Modifier.sendEmphasisGesture(
                    enabled = enabled,
                    message = message,
                    stateMachine = stateMachine,
                    publish = { latestPublish(it) },
                    onEnterHold = { latestEnterHold() },
                    onCancel = { latestCancel() },
                    onSend = { emphasis -> latestEmphasizedSend(emphasis) },
                    dragRangePx = dragRangePx,
                    verticalCancelPx = verticalCancelPx,
                    touchSlopPx = touchSlopPx
                ) else Modifier.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitPointerEventScope {
                        val down = awaitFirstDown()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                latestQuickSend()
                                break
                            }
                        } while (true)
                    }
                }
            )
            .graphicsLayer { scaleX = scale * popScale.value; scaleY = scale * popScale.value }
            .semantics {
                role = Role.Button
                contentDescription = sendDescription
                if (!enabled) disabled()
                onClick { if (enabled) { latestQuickSend(); true } else false }
            },
        shape = CircleShape,
        color = activeContainer,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.phase in setOf(
                    SendInteractionPhase.Holding,
                    SendInteractionPhase.Adjusting,
                    SendInteractionPhase.AtMaximum
                )
            ) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    drawArc(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.76f),
                        startAngle = -90f,
                        sweepAngle = 360f * state.progress,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(25.dp))
        }
    }
}

private fun Modifier.sendEmphasisGesture(
    enabled: Boolean,
    message: String,
    stateMachine: SendEmphasisStateMachine,
    publish: (SendEmphasisUiState) -> Unit,
    onEnterHold: () -> Unit,
    onCancel: () -> Unit,
    onSend: (MessageEmphasis) -> Unit,
    dragRangePx: Float,
    verticalCancelPx: Float,
    touchSlopPx: Float
): Modifier = pointerInput(enabled, message, dragRangePx, verticalCancelPx, touchSlopPx) {
    val snapshot = message.trim()
    if (!enabled || snapshot.isBlank()) return@pointerInput
    coroutineScope {
        while (isActive) {
            var cancelled = false
            var completed = false
            try {
                awaitPointerEventScope {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val origin = down.position
                    val startedAt = SystemClock.uptimeMillis()
                    var lastUptime = down.uptimeMillis
                    var holding = false
                    var adjusting = false
                    var dragBaseProgress = 0f
                    publish(stateMachine.press(snapshot))

                    fun elapsed() = max(
                        lastUptime - down.uptimeMillis,
                        SystemClock.uptimeMillis() - startedAt
                    ).coerceAtLeast(0L)
                    fun enterHold(time: Long) {
                        if (holding) return
                        holding = true
                        publish(stateMachine.beginHolding())
                        onEnterHold()
                        publish(stateMachine.updateProgress(SendEmphasisMath.holdProgress(
                            time - ChatExpressiveTokens.HoldThresholdMillis,
                            ChatExpressiveTokens.HoldGrowthDurationMillis
                        )))
                    }

                    while (true) {
                        val before = elapsed()
                        val event = when {
                            !holding -> withTimeoutOrNull(
                                (ChatExpressiveTokens.HoldThresholdMillis - before).coerceAtLeast(1L)
                            ) { awaitPointerEvent(PointerEventPass.Main) }
                            !adjusting -> withTimeoutOrNull(16L) { awaitPointerEvent(PointerEventPass.Main) }
                            else -> awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            val time = elapsed()
                            if (!holding) enterHold(time)
                            if (!adjusting) publish(stateMachine.updateProgress(SendEmphasisMath.holdProgress(
                                time - ChatExpressiveTokens.HoldThresholdMillis,
                                ChatExpressiveTokens.HoldGrowthDurationMillis
                            )))
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) { cancelled = true; break }
                        lastUptime = change.uptimeMillis
                        val time = elapsed()
                        if (!holding && time >= ChatExpressiveTokens.HoldThresholdMillis) enterHold(time)
                        else if (holding && !adjusting) publish(stateMachine.updateProgress(
                            SendEmphasisMath.holdProgress(
                                time - ChatExpressiveTokens.HoldThresholdMillis,
                                ChatExpressiveTokens.HoldGrowthDurationMillis
                            )
                        ))
                        if (abs(change.position.y - origin.y) >= verticalCancelPx) {
                            cancelled = true
                            change.consume()
                            break
                        }
                        if (!change.pressed) { change.consume(); break }
                        if (holding) {
                            val deltaX = change.position.x - origin.x
                            if (!adjusting && abs(deltaX) >= touchSlopPx) {
                                adjusting = true
                                dragBaseProgress = stateMachine.state.progress
                                publish(stateMachine.beginAdjusting())
                            }
                            if (adjusting) publish(stateMachine.updateProgress(
                                SendEmphasisMath.dragProgress(dragBaseProgress, deltaX, dragRangePx)
                            ))
                        }
                        change.consume()
                    }
                }
                if (cancelled) onCancel() else {
                    val emphasis = stateMachine.release()
                    publish(stateMachine.state)
                    completed = true
                    if (emphasis != null) onSend(emphasis)
                }
            } finally {
                if (!completed && !cancelled) onCancel()
            }
        }
    }
}

@Composable
private fun SendStarBurst(
    event: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled || event == 0) return
    val progress = remember { Animatable(1f) }
    val color = MaterialTheme.colorScheme.tertiary
    LaunchedEffect(event) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 460))
    }
    Canvas(modifier.graphicsLayer { alpha = (1f - progress.value).coerceIn(0f, 1f) }) {
        val origin = center
        repeat(7) { index ->
            val angle = -PI.toFloat() / 2f + index * (2f * PI.toFloat() / 7f)
            val radius = size.minDimension * (0.12f + progress.value * 0.38f)
            val starCenter = androidx.compose.ui.geometry.Offset(
                x = origin.x + cos(angle) * radius,
                y = origin.y + sin(angle) * radius
            )
            val starSize = size.minDimension * (0.085f - progress.value * 0.035f)
            drawPath(
                path = sparklePath(starCenter, starSize.coerceAtLeast(2f)),
                color = if (index % 2 == 0) color else androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

private fun sparklePath(center: androidx.compose.ui.geometry.Offset, radius: Float): Path = Path().apply {
    repeat(8) { index ->
        val pointRadius = if (index % 2 == 0) radius else radius * 0.24f
        val angle = -PI.toFloat() / 2f + index * PI.toFloat() / 4f
        val x = center.x + cos(angle) * pointRadius
        val y = center.y + sin(angle) * pointRadius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiPanel(onEmoji: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        maxItemsInEachRow = 8,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickEmojis.forEach { emoji ->
            val description = stringResource(R.string.insert_emoji, emoji)
            IconButton(
                modifier = Modifier.size(ChatExpressiveTokens.MinimumTouchTarget)
                    .semantics { contentDescription = description },
                onClick = { onEmoji(emoji) }
            ) { Text(emoji, fontSize = 22.sp) }
        }
    }
}

@Composable
private fun emphasisLabel(emphasis: MessageEmphasis): String = stringResource(
    when (emphasis) {
        MessageEmphasis.Normal -> R.string.emphasis_normal
        MessageEmphasis.Low -> R.string.emphasis_low
        MessageEmphasis.Medium -> R.string.emphasis_medium
        MessageEmphasis.High -> R.string.emphasis_high
        MessageEmphasis.Maximum -> R.string.emphasis_maximum
    }
)

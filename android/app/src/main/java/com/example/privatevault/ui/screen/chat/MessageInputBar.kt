package com.example.privatevault.ui.screen.chat

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
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
import com.example.privatevault.model.MessageEmphasis
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
    onSend: (String, Int) -> Unit,
    onAttachFile: (String) -> Unit,
    pendingAttachment: ChatAttachment?,
    onRemovePendingAttachment: () -> Unit,
    onSendAttachment: (ChatAttachment, String) -> Unit,
    attachmentManager: AttachmentManager,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var emojiPanelVisible by rememberSaveable { mutableStateOf(false) }
    val trimmed = text.trim()
    val canSend = trimmed.isNotBlank() || pendingAttachment != null
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
    fun resetGesture() { publish(stateMachine.reset()) }
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
    fun commit(emphasis: MessageEmphasis = MessageEmphasis.Normal) {
        if (!canSend) return
        cancelResetJob?.cancel()
        pendingAttachment?.let { onSendAttachment(it, trimmed) }
            ?: onSend(trimmed, emphasis.storedValue)
        if (animationsEnabled) sendBurstNonce += 1
        updateText("")
        emojiPanelVisible = false
        resetGesture()
    }
    fun toggleEmojiPanel() {
        cancelGesture()
        emojiPanelVisible = !emojiPanelVisible
        if (emojiPanelVisible) {
            focusManager.clearFocus()
            keyboard?.hide()
        } else {
            focusRequester.requestFocus()
            keyboard?.show()
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
    LaunchedEffect(trimmed, pendingAttachment) {
        if (trimmed.isBlank() && gestureState.phase != SendInteractionPhase.Idle) {
            cancelGesture(withHaptic = false)
        }
    }
    LaunchedEffect(gestureState.popCount) {
        if (gestureState.popCount == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
            launch {
                previewShake.snapTo(0f)
                previewShake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 420
                        -7f at 45
                        7f at 90
                        -6f at 135
                        5f at 180
                        -4f at 225
                        3f at 275
                        -2f at 330
                        0f at 420
                    }
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .background(
                Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    0.38f to MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.82f),
                    1f to MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
            .swipeUpToOpenKeyboard(with(density) { 28.dp.toPx() }) {
                emojiPanelVisible = false
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(start = 12.dp, top = 28.dp, end = 12.dp, bottom = 8.dp)
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
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(if (animationsEnabled) spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ) else snap())
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
                    visible = pendingAttachment != null,
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    pendingAttachment?.let {
                        PendingAttachmentPreview(it, attachmentManager, onRemovePendingAttachment)
                    }
                }
                AnimatedVisibility(
                    visible = emojiPanelVisible,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    EmojiPanel { emoji -> updateText(text + emoji) }
                }
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    ComposerActionButton(
                        icon = Icons.Default.Add,
                        description = stringResource(R.string.attach_file),
                        onClick = { cancelGesture(); onAttachFile("*/*") }
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp, max = 136.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = text,
                                onValueChange = ::updateText,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message-composer-input")
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { if (it.isFocused) emojiPanelVisible = false },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(onSend = { commit() }),
                                maxLines = 5,
                                decorationBox = { inner ->
                                    Box(Modifier.padding(vertical = 16.dp)) {
                                        if (text.isEmpty()) Text(
                                            stringResource(R.string.message_placeholder),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                                        )
                                        inner()
                                    }
                                }
                            )
                            IconButton(onClick = ::toggleEmojiPanel) {
                                Icon(
                                    Icons.Outlined.EmojiEmotions,
                                    contentDescription = stringResource(
                                        if (emojiPanelVisible) R.string.close_emoji_picker else R.string.open_emoji_picker
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = canSend,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        ExpressiveSendControl(
                            enabled = canSend,
                            holdEnabled = pendingAttachment == null && trimmed.isNotBlank(),
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

@Composable
private fun ComposerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(27.dp))
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
        value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 900) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 4.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            if (bitmap != null && attachment.mimeType.startsWith("image/")) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_attachment, attachment.name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 148.dp, max = 220.dp)
                        .clip(RoundedCornerShape(26.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    attachment.name,
                    modifier = Modifier.padding(22.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Surface(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp),
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
        MaterialTheme.typography.bodyLarge.copy(fontSize = 21.sp, lineHeight = 28.sp)
    )
    val emphasis = MessageEmphasis.fromProgress(progress)
    val stateLabel = if (state.phase == SendInteractionPhase.Cancelling) {
        stringResource(R.string.send_cancelled)
    } else emphasisLabel(emphasis)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 54.dp, top = 12.dp, end = 16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            modifier = Modifier
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
                },
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
            .size(54.dp)
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

private fun Modifier.swipeUpToOpenKeyboard(
    thresholdPx: Float,
    onSwipeUp: () -> Unit
): Modifier = pointerInput(thresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var triggered = false
        while (!triggered) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.position.y - down.position.y <= -thresholdPx) {
                triggered = true
                onSwipeUp()
            }
            if (!change.pressed) break
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

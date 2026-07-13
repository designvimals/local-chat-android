package com.example.privatevault.ui.screen.chat

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privatevault.R
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.model.Message
import com.example.privatevault.network.BackendRegistrationState
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private const val MessageGroupWindowMinutes = 5L

private data class PresentedMessage(
    val message: Message,
    val isMine: Boolean,
    val groupedWithPrevious: Boolean,
    val groupedWithNext: Boolean
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    pairingCode: String,
    pairingAvailable: Boolean,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit,
    onOpenSettings: () -> Unit,
    onAttachFile: (String) -> Unit,
    pendingAttachment: ChatAttachment?,
    onRemovePendingAttachment: () -> Unit,
    onSendAttachment: (ChatAttachment, String) -> Unit,
    attachmentManager: AttachmentManager,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerConnected by viewModel.viewerConnected.collectAsStateWithLifecycle()
    val remoteTyping by viewModel.remoteTyping.collectAsStateWithLifecycle()
    val attachmentVersion by attachmentManager.updates.collectAsStateWithLifecycle()
    var selectedImage by remember { mutableStateOf<ChatAttachment?>(null) }
    var enteringAttachmentId by remember { mutableStateOf<String?>(null) }
    val presented = remember(messages) { presentMessages(messages, viewModel) }

    LaunchedEffect(enteringAttachmentId) {
        if (enteringAttachmentId != null) {
            kotlinx.coroutines.delay(1_100)
            enteringAttachmentId = null
        }
    }

    SharedTransitionLayout(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedImage,
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            contentKey = { it?.id ?: "conversation" },
            label = "image-viewer-morph"
        ) { image ->
            if (image == null) {
                ConversationScaffold(
                    presentedMessages = presented,
                    viewerConnected = viewerConnected,
                    remoteTyping = remoteTyping,
                    pairingCode = pairingCode,
                    pairingAvailable = pairingAvailable,
                    registrationState = registrationState,
                    onRetryRegistration = onRetryRegistration,
                    onOpenSettings = onOpenSettings,
                    onAttachFile = onAttachFile,
                    pendingAttachment = pendingAttachment,
                    onRemovePendingAttachment = onRemovePendingAttachment,
                    onSendAttachment = { attachment, caption ->
                        enteringAttachmentId = attachment.id
                        onSendAttachment(attachment, caption)
                    },
                    onSend = viewModel::send,
                    onTextChanged = viewModel::composerChanged,
                    onToggleReaction = viewModel::toggleReaction,
                    reactedByMe = { message ->
                        message.reactions.filter(viewModel::isCurrentUserReaction).mapTo(mutableSetOf()) { it.emoji }
                    },
                    onImageClick = { selectedImage = it },
                    imageModifier = { attachment ->
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("attachment-${attachment.id}"),
                            animatedVisibilityScope = this@AnimatedContent
                        )
                    },
                    enteringAttachmentId = enteringAttachmentId,
                    attachmentManager = attachmentManager,
                    attachmentVersion = attachmentVersion
                )
            } else {
                FullScreenImageViewer(
                    attachment = image,
                    attachmentManager = attachmentManager,
                    onClose = { selectedImage = null },
                    imageModifier = Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("attachment-${image.id}"),
                        animatedVisibilityScope = this@AnimatedContent
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationScaffold(
    presentedMessages: List<PresentedMessage>,
    viewerConnected: Boolean,
    remoteTyping: Boolean,
    pairingCode: String,
    pairingAvailable: Boolean,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit,
    onOpenSettings: () -> Unit,
    onAttachFile: (String) -> Unit,
    pendingAttachment: ChatAttachment?,
    onRemovePendingAttachment: () -> Unit,
    onSendAttachment: (ChatAttachment, String) -> Unit,
    onSend: (String, Int) -> Unit,
    onTextChanged: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    reactedByMe: (Message) -> Set<String>,
    onImageClick: (ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    enteringAttachmentId: String?,
    attachmentManager: AttachmentManager,
    attachmentVersion: Long
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val isFollowingLatest by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }
    val latestMessageId = presentedMessages.lastOrNull()?.message?.id

    LaunchedEffect(latestMessageId, remoteTyping, imeVisible, enteringAttachmentId) {
        if (presentedMessages.isEmpty() && !remoteTyping) return@LaunchedEffect
        if (!imeVisible && !isFollowingLatest) return@LaunchedEffect
        yield()
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) {
            if (animationsEnabled && enteringAttachmentId != null) {
                // Keep the growing image anchored to the bottom. The list content above it
                // visibly moves with the spring instead of the image appearing over it.
                repeat(30) {
                    listState.scrollToItem(last)
                    delay(16)
                }
            } else if (animationsEnabled) {
                listState.animateScrollToItem(last)
            } else {
                listState.scrollToItem(last)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            ExpressiveConversationHeader(
                viewerConnected = viewerConnected,
                remoteTyping = remoteTyping,
                registrationState = registrationState,
                onOpenSettings = onOpenSettings
            )
        },
        bottomBar = {
            MessageInputBar(
                onSend = onSend,
                onAttachFile = onAttachFile,
                pendingAttachment = pendingAttachment,
                onRemovePendingAttachment = onRemovePendingAttachment,
                onSendAttachment = onSendAttachment,
                attachmentManager = attachmentManager,
                onTextChanged = onTextChanged
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).imeNestedScroll(),
            contentPadding = PaddingValues(
                start = 14.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = 14.dp,
                bottom = padding.calculateBottomPadding() + 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (!viewerConnected && pairingAvailable) {
                item("pairing-card") {
                    PairingCard(pairingCode, registrationState, onRetryRegistration)
                }
            }
            if (presentedMessages.isEmpty()) {
                item("empty-chat") { EmptyConversation(viewerConnected, pairingAvailable) }
            } else {
                items(presentedMessages, key = { it.message.id }) { item ->
                    val isEnteringImage = item.message.attachment?.id == enteringAttachmentId
                    ImageMessageEntrance(
                        messageId = item.message.id,
                        animate = animationsEnabled && isEnteringImage,
                        modifier = if (animationsEnabled) Modifier.animateItem(
                            fadeInSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            placementSpec = spring(
                                dampingRatio = if (isEnteringImage) {
                                    Spring.DampingRatioMediumBouncy
                                } else Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                        ) else Modifier
                    ) {
                        MessageBubble(
                            message = item.message,
                            isMine = item.isMine,
                            showSenderName = !item.isMine && !item.groupedWithPrevious,
                            showAvatar = !item.isMine && !item.groupedWithNext,
                            groupedWithPrevious = item.groupedWithPrevious,
                            groupedWithNext = item.groupedWithNext,
                            reactedByMe = reactedByMe(item.message),
                            onToggleReaction = { onToggleReaction(item.message.id, it) },
                            onImageClick = onImageClick,
                            imageModifier = imageModifier,
                            animateImageEntrance = false,
                            attachmentManager = attachmentManager,
                            attachmentVersion = attachmentVersion
                        )
                    }
                }
            }
            item("typing-indicator") {
                AnimatedVisibility(
                    visible = remoteTyping,
                    enter = fadeIn() + slideInVertically(
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    ) { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 4 }
                ) { TypingIndicator(animationsEnabled) }
            }
        }
    }
}

@Composable
private fun ExpressiveConversationHeader(
    viewerConnected: Boolean,
    remoteTyping: Boolean,
    registrationState: BackendRegistrationState,
    onOpenSettings: () -> Unit
) {
    val status = when {
        remoteTyping -> stringResource(R.string.typing_indicator, stringResource(R.string.contact_name))
        viewerConnected -> stringResource(R.string.status_connected)
        registrationState is BackendRegistrationState.Registered -> stringResource(R.string.status_code_active)
        registrationState is BackendRegistrationState.Failed -> stringResource(R.string.status_relay_offline)
        else -> stringResource(R.string.status_connecting_relay)
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                .height(58.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.contact_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 29.sp),
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.align(Alignment.CenterStart).width(142.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(
                        if (viewerConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            RoundHeaderButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Settings, stringResource(R.string.settings), Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun RoundHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun ImageMessageEntrance(
    messageId: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val visibility = remember(messageId) {
        MutableTransitionState(!animate).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier,
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
            initialOffsetY = { it.coerceAtMost(320) }
        ) + scaleIn(
            initialScale = 0.86f,
            transformOrigin = TransformOrigin(0.5f, 1f),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(120))
    ) { content() }
}

@Composable
private fun FullScreenImageViewer(
    attachment: ChatAttachment,
    attachmentManager: AttachmentManager,
    onClose: () -> Unit,
    imageModifier: Modifier
) {
    BackHandler(onBack = onClose)
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id) {
        value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 2_560) }
    }
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (bitmap != null) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val imageAspect = bitmap!!.width.toFloat() / bitmap!!.height.toFloat().coerceAtLeast(1f)
                val viewportAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                val fittedSize = if (viewportAspect > imageAspect) {
                    Modifier.fillMaxHeight().aspectRatio(imageAspect)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(imageAspect)
                }
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_attachment, attachment.name),
                    modifier = fittedSize
                        .then(imageModifier)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(attachment.id) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                            })
                        }
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = androidx.compose.ui.graphics.Color.White)
        }
        Surface(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(52.dp),
            shape = CircleShape,
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f),
            contentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, stringResource(R.string.close), Modifier.size(26.dp))
            }
        }
    }
}

private fun presentMessages(messages: List<Message>, viewModel: ChatViewModel): List<PresentedMessage> =
    messages.mapIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        PresentedMessage(
            message = message,
            isMine = viewModel.isMine(message),
            groupedWithPrevious = previous != null && sameMessageGroup(previous, message),
            groupedWithNext = next != null && sameMessageGroup(message, next)
        )
    }

private fun sameMessageGroup(first: Message, second: Message): Boolean {
    if (first.senderDeviceId != second.senderDeviceId) return false
    return runCatching {
        Duration.between(Instant.parse(first.timestamp), Instant.parse(second.timestamp)).abs().toMinutes() <=
            MessageGroupWindowMinutes
    }.getOrDefault(false)
}

@Composable
private fun TypingIndicator(animationsEnabled: Boolean) {
    val alphas = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "typing-dots")
        List(3) { index ->
            transition.animateFloat(
                0.32f,
                1f,
                infiniteRepeatable(
                    tween(520, index * 110, FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "typing-$index"
            )
        }
    } else emptyList()
    Row(
        Modifier.fillMaxWidth().padding(start = 52.dp, top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f),
            shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 7.dp)
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.spacedBy(5.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier.size(7.dp).clip(CircleShape).background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (animationsEnabled) alphas[index].value else 1f
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingCard(
    pairingCode: String,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(9.dp))
                Text(stringResource(R.string.pairing_card_title), style = MaterialTheme.typography.titleMedium)
            }
            when (registrationState) {
                is BackendRegistrationState.Registered -> {
                    Text(stringResource(R.string.pairing_card_registered_body))
                    SelectionContainer {
                        Text(
                            pairingCode.chunked(3).joinToString("  "),
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is BackendRegistrationState.Failed -> {
                    Text(registrationState.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetryRegistration) { Text(stringResource(R.string.retry_relay_connection)) }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.pairing_registering))
                }
            }
        }
    }
}

@Composable
private fun EmptyConversation(viewerConnected: Boolean, pairingAvailable: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("↗", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(14.dp))
        Text(
            if (viewerConnected) stringResource(R.string.empty_connected_title)
            else if (pairingAvailable) stringResource(R.string.empty_pairing_title)
            else stringResource(R.string.empty_paired_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            if (viewerConnected) stringResource(R.string.empty_connected_body)
            else if (pairingAvailable) stringResource(R.string.empty_pairing_body)
            else stringResource(R.string.empty_paired_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

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
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
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
import com.example.privatevault.model.DeleteScope
import com.example.privatevault.model.Message
import com.example.privatevault.model.canonicalAttachments
import com.example.privatevault.network.BackendRegistrationState
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val MessageGroupWindowMinutes = 5L

private data class PresentedMessage(
    val message: Message,
    val isMine: Boolean,
    val groupedWithPrevious: Boolean,
    val groupedWithNext: Boolean
)

private data class ImageViewerState(
    val messageId: String,
    val images: List<ChatAttachment>,
    val initialIndex: Int
) {
    val initialImage: ChatAttachment get() = images[initialIndex]
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    pairingCode: String,
    pairingAvailable: Boolean,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit,
    onOpenSettings: () -> Unit,
    onAttachFiles: () -> Unit,
    pendingAttachments: List<ChatAttachment>,
    onRemovePendingAttachment: (String) -> Unit,
    onSendAttachments: suspend (List<ChatAttachment>, String, String?) -> Result<Message>,
    attachmentManager: AttachmentManager,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val viewerConnected by viewModel.viewerConnected.collectAsStateWithLifecycle()
    val remoteTyping by viewModel.remoteTyping.collectAsStateWithLifecycle()
    var imageViewer by remember { mutableStateOf<ImageViewerState?>(null) }
    val currentDeviceId = remember(viewModel) { viewModel.currentDeviceId() }
    val visibleMessages = remember(messages, currentDeviceId) {
        messages.filterNot { currentDeviceId in it.deletedForDeviceIds }
    }
    val presented = remember(visibleMessages) { presentMessages(visibleMessages, viewModel) }
    val listState = rememberLazyListState()
    var initialPositioningComplete by remember { mutableStateOf(false) }
    val initialTargetId = remember(presented) {
        presented.firstOrNull {
            !it.isMine && it.message.status != "read" && it.message.deletedAt == null
        }?.message?.id ?: presented.lastOrNull()?.message?.id
    }

    SharedTransitionLayout(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        AnimatedContent(
            targetState = imageViewer,
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            contentKey = { it?.messageId ?: "conversation" },
            label = "image-viewer-morph"
        ) { viewerState ->
            if (viewerState == null) {
                ConversationScaffold(
                    presentedMessages = presented,
                    allMessages = messages,
                    currentDeviceId = currentDeviceId,
                    listState = listState,
                    initialTargetId = initialTargetId,
                    initialPositioningComplete = initialPositioningComplete,
                    onInitialPositioned = {
                        initialPositioningComplete = true
                        viewModel.markIncomingRead()
                    },
                    viewerConnected = viewerConnected,
                    remoteTyping = remoteTyping,
                    pairingCode = pairingCode,
                    pairingAvailable = pairingAvailable,
                    registrationState = registrationState,
                    onRetryRegistration = onRetryRegistration,
                    onOpenSettings = onOpenSettings,
                    onAttachFiles = onAttachFiles,
                    pendingAttachments = pendingAttachments,
                    onRemovePendingAttachment = onRemovePendingAttachment,
                    onSendAttachments = onSendAttachments,
                    onSend = viewModel::send,
                    onEdit = viewModel::editMessage,
                    onDelete = viewModel::deleteMessages,
                    canEdit = viewModel::canEdit,
                    isMine = viewModel::isMine,
                    onTextChanged = viewModel::composerChanged,
                    onToggleReaction = viewModel::toggleReaction,
                    reactedByMe = { message ->
                        message.reactions.filter(viewModel::isCurrentUserReaction).mapTo(mutableSetOf()) { it.emoji }
                    },
                    onImageClick = { message, attachment ->
                        val images = message.canonicalAttachments.filter { it.mimeType.startsWith("image/") }
                        val index = images.indexOfFirst { it.id == attachment.id }
                        if (index >= 0) imageViewer = ImageViewerState(message.id, images, index)
                    },
                    imageModifier = { attachment ->
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("attachment-${attachment.id}"),
                            animatedVisibilityScope = this@AnimatedContent
                        )
                    },
                    attachmentManager = attachmentManager
                )
            } else {
                FullScreenImageViewer(
                    images = viewerState.images,
                    initialIndex = viewerState.initialIndex,
                    attachmentManager = attachmentManager,
                    onClose = { imageViewer = null },
                    sharedImageId = viewerState.initialImage.id,
                    sharedImageModifier = Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("attachment-${viewerState.initialImage.id}"),
                        animatedVisibilityScope = this@AnimatedContent
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScaffold(
    presentedMessages: List<PresentedMessage>,
    allMessages: List<Message>,
    currentDeviceId: String,
    listState: LazyListState,
    initialTargetId: String?,
    initialPositioningComplete: Boolean,
    onInitialPositioned: () -> Unit,
    viewerConnected: Boolean,
    remoteTyping: Boolean,
    pairingCode: String,
    pairingAvailable: Boolean,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit,
    onOpenSettings: () -> Unit,
    onAttachFiles: () -> Unit,
    pendingAttachments: List<ChatAttachment>,
    onRemovePendingAttachment: (String) -> Unit,
    onSendAttachments: suspend (List<ChatAttachment>, String, String?) -> Result<Message>,
    onSend: suspend (String, Int, String?) -> Result<Message>,
    onEdit: suspend (String, String) -> Result<Message>,
    onDelete: suspend (Set<String>, DeleteScope) -> Result<List<Message>>,
    canEdit: (Message) -> Boolean,
    isMine: (Message) -> Boolean,
    onTextChanged: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    reactedByMe: (Message) -> Set<String>,
    onImageClick: (Message, ChatAttachment) -> Unit,
    imageModifier: @Composable (ChatAttachment) -> Modifier,
    attachmentManager: AttachmentManager
) {
    val scope = rememberCoroutineScope()
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
    var previousFollowInputs by remember {
        mutableStateOf(Triple(latestMessageId, remoteTyping, imeVisible))
    }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var contextAnchorId by remember { mutableStateOf<String?>(null) }
    var replyMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val deleteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var enteringMessageId by remember { mutableStateOf<String?>(null) }
    val messagesById = remember(allMessages) { allMessages.associateBy(Message::id) }
    val selectedMessages = remember(selectedIds, allMessages) { allMessages.filter { it.id in selectedIds } }
    val selectedAllMine = selectedMessages.isNotEmpty() &&
        selectedMessages.all { isMine(it) && it.deletedAt == null }

    fun clearSelection() {
        selectedIds = emptySet()
        contextAnchorId = null
    }

    BackHandler(enabled = selectedIds.isNotEmpty()) { clearSelection() }

    LaunchedEffect(initialTargetId, initialPositioningComplete, presentedMessages.size) {
        if (initialPositioningComplete || initialTargetId == null) return@LaunchedEffect
        val targetIndex = presentedMessages.indexOfFirst { it.message.id == initialTargetId }
        if (targetIndex < 0) return@LaunchedEffect
        val prefix = if (!viewerConnected && pairingAvailable) 1 else 0
        listState.scrollToItem(targetIndex + prefix)
        androidx.compose.runtime.withFrameNanos { }
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == initialTargetId }?.let { target ->
            val viewportCenter = (
                listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset
            ) / 2
            val targetCenter = target.offset + target.size / 2
            listState.scrollBy((targetCenter - viewportCenter).toFloat())
        }
        onInitialPositioned()
    }

    LaunchedEffect(
        initialPositioningComplete,
        latestMessageId,
        remoteTyping,
        imeVisible,
        enteringMessageId
    ) {
        val currentInputs = Triple(latestMessageId, remoteTyping, imeVisible)
        if (!initialPositioningComplete) {
            previousFollowInputs = currentInputs
            return@LaunchedEffect
        }
        val isLocalSendEntrance = latestMessageId != null && latestMessageId == enteringMessageId
        if (currentInputs == previousFollowInputs && !isLocalSendEntrance) return@LaunchedEffect
        if (presentedMessages.isEmpty() && !remoteTyping) return@LaunchedEffect
        if (!imeVisible && !isFollowingLatest && !isLocalSendEntrance) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        previousFollowInputs = currentInputs
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) {
            if (animationsEnabled && (!imeVisible || isLocalSendEntrance)) {
                listState.animateScrollToItem(last)
            } else {
                listState.scrollToItem(last)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            ExpressiveConversationHeader(
                viewerConnected = viewerConnected,
                remoteTyping = remoteTyping,
                registrationState = registrationState,
                onOpenSettings = onOpenSettings,
                selectedCount = selectedIds.size,
                onClearSelection = ::clearSelection
            )
        },
        bottomBar = {
            MessageInputBar(
                onSend = onSend,
                onEdit = onEdit,
                onAttachFiles = onAttachFiles,
                pendingAttachments = pendingAttachments,
                onRemovePendingAttachment = onRemovePendingAttachment,
                onSendAttachments = onSendAttachments,
                attachmentManager = attachmentManager,
                onTextChanged = onTextChanged,
                replyMessage = replyMessage,
                editingMessage = editingMessage,
                onClearContext = { replyMessage = null; editingMessage = null },
                onSent = { sent ->
                    enteringMessageId = sent.id
                    scope.launch {
                        delay(900)
                        if (enteringMessageId == sent.id) enteringMessageId = null
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .clearSelectionOnUnconsumedTap(selectedIds.isNotEmpty(), ::clearSelection),
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
                items(
                    presentedMessages,
                    key = { it.message.id },
                    contentType = {
                        if (it.message.canonicalAttachments.isNotEmpty()) "attachment-message" else "text-message"
                    }
                ) { item ->
                    SentMessageEntrance(
                        messageId = item.message.id,
                        animate = animationsEnabled && item.message.id == enteringMessageId
                    ) {
                        val selected = item.message.id in selectedIds
                        val itemCanEdit = remember(
                            item.message.id,
                            item.message.updatedAt,
                            item.message.deletedAt
                        ) { canEdit(item.message) }
                        val itemReactions = remember(item.message.reactions) { reactedByMe(item.message) }
                        val replyTarget = item.message.replyToMessageId
                            ?.let(messagesById::get)
                            ?.takeUnless { currentDeviceId in it.deletedForDeviceIds }
                        MessageBubble(
                            message = item.message,
                            isMine = item.isMine,
                            showSenderName = !item.isMine && !item.groupedWithPrevious,
                            showAvatar = !item.isMine && !item.groupedWithNext,
                            groupedWithPrevious = item.groupedWithPrevious,
                            groupedWithNext = item.groupedWithNext,
                            reactedByMe = itemReactions,
                            selected = selected,
                            selectionCount = selectedIds.size,
                            selectionMode = selectedIds.isNotEmpty(),
                            showContextMenu = selected && contextAnchorId == item.message.id,
                            replyMessage = replyTarget,
                            replyMessageIsMine = replyTarget?.let(isMine) == true,
                            canEdit = itemCanEdit,
                            highlighted = highlightedMessageId == item.message.id,
                            onToggleReaction = { onToggleReaction(item.message.id, it) },
                            onToggleSelection = {
                                selectedIds = if (selected) selectedIds - item.message.id else selectedIds + item.message.id
                                contextAnchorId = if (selected) selectedIds.minus(item.message.id).lastOrNull() else item.message.id
                            },
                            onSelect = {
                                selectedIds = selectedIds + item.message.id
                                contextAnchorId = item.message.id
                            },
                            onDismissContextMenu = { contextAnchorId = null },
                            onReply = {
                                if (item.message.deletedAt == null) replyMessage = item.message
                                editingMessage = null
                                clearSelection()
                            },
                            onEdit = {
                                if (itemCanEdit) editingMessage = item.message
                                replyMessage = null
                                clearSelection()
                            },
                            onDelete = { deleteDialogVisible = true; contextAnchorId = null },
                            onReplyQuoteClick = { targetId ->
                                val targetIndex = presentedMessages.indexOfFirst { it.message.id == targetId }
                                if (targetIndex >= 0) scope.launch {
                                    val prefix = if (!viewerConnected && pairingAvailable) 1 else 0
                                    listState.animateScrollToItem(targetIndex + prefix)
                                    highlightedMessageId = targetId
                                    delay(850)
                                    if (highlightedMessageId == targetId) highlightedMessageId = null
                                }
                            },
                            onImageClick = { attachment -> onImageClick(item.message, attachment) },
                            imageModifier = imageModifier,
                            attachmentManager = attachmentManager
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

    if (deleteDialogVisible) {
        ModalBottomSheet(
            onDismissRequest = { deleteDialogVisible = false },
            sheetState = deleteSheetState
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.delete_message_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.delete_for_me), color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        deleteDialogVisible = false
                        scope.launch { if (onDelete(selectedIds, DeleteScope.ForMe).isSuccess) clearSelection() }
                    }
                )
                if (selectedAllMine) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.delete_for_both), color = MaterialTheme.colorScheme.error)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.fillMaxWidth().clickable {
                            deleteDialogVisible = false
                            scope.launch {
                                if (onDelete(selectedIds, DeleteScope.ForEveryone).isSuccess) clearSelection()
                            }
                        }
                    )
                }
                TextButton(
                    onClick = { deleteDialogVisible = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun ExpressiveConversationHeader(
    viewerConnected: Boolean,
    remoteTyping: Boolean,
    registrationState: BackendRegistrationState,
    onOpenSettings: () -> Unit,
    selectedCount: Int,
    onClearSelection: () -> Unit
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
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 18.dp, vertical = 8.dp)
                .height(58.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (selectedCount > 0) stringResource(R.string.selected_count, selectedCount)
                else stringResource(R.string.contact_name),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 29.sp),
                fontWeight = FontWeight.SemiBold
            )
            if (selectedCount == 0) Row(
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
            IconButton(
                onClick = if (selectedCount > 0) onClearSelection else onOpenSettings,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
            ) {
                Icon(
                    if (selectedCount > 0) Icons.Default.Close else Icons.Default.Settings,
                    stringResource(if (selectedCount > 0) R.string.clear_selection else R.string.settings),
                    Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun SentMessageEntrance(
    messageId: String,
    animate: Boolean,
    content: @Composable () -> Unit
) {
    if (!animate) {
        content()
        return
    }
    val initialOffsetPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val visibility = remember(messageId) {
        MutableTransitionState(!animate).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMedium
            ),
            initialOffsetY = { it.coerceAtMost(initialOffsetPx) }
        ) + scaleIn(
            initialScale = 0.96f,
            transformOrigin = TransformOrigin(0.5f, 1f),
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(120))
    ) { content() }
}

private fun Modifier.clearSelectionOnUnconsumedTap(
    enabled: Boolean,
    onTap: () -> Unit
): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var consumed = down.isConsumed
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            consumed = consumed || change.isConsumed
            moved = moved || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            if (!change.pressed) {
                if (!consumed && !moved) onTap()
                break
            }
        }
    }
}

internal fun imageDismissTarget(
    offset: Float,
    velocity: Float,
    distanceThreshold: Float,
    velocityThreshold: Float,
    viewportHeight: Float
): Float? {
    val projectedOffset = offset + velocity * 0.18f
    val shouldDismiss = abs(offset) >= distanceThreshold ||
        abs(projectedOffset) >= distanceThreshold ||
        abs(velocity) >= velocityThreshold
    if (!shouldDismiss) return null
    val direction = if (abs(velocity) >= velocityThreshold * 0.35f) velocity else projectedOffset
    return if (direction < 0f) -viewportHeight else viewportHeight
}

@Composable
private fun FullScreenImageViewer(
    images: List<ChatAttachment>,
    initialIndex: Int,
    attachmentManager: AttachmentManager,
    onClose: () -> Unit,
    sharedImageId: String,
    sharedImageModifier: Modifier
) {
    require(images.isNotEmpty()) { "The image viewer requires at least one image." }
    BackHandler(onBack = onClose)
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(images.indices)) { images.size }
    val imageScales = remember(images) {
        mutableStateMapOf<String, Float>().apply { images.forEach { put(it.id, 1f) } }
    }
    val currentImage = images[pagerState.currentPage.coerceIn(images.indices)]
    val currentScale = imageScales[currentImage.id] ?: 1f
    var dismissOffset by remember { mutableFloatStateOf(0f) }
    var dismissSettleJob by remember { mutableStateOf<Job?>(null) }
    val viewerScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dismissDistancePx = with(density) { 96.dp.toPx() }
    val dismissVelocityPx = with(density) { 900.dp.toPx() }
    var viewportHeightPx by remember { mutableFloatStateOf(1f) }
    val scrimAlpha = (1f - abs(dismissOffset) / (viewportHeightPx * 0.82f))
        .coerceIn(0.18f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = scrimAlpha))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationY = dismissOffset }
                .pointerInput(currentImage.id, currentScale) {
                    if (currentScale > 1.01f) return@pointerInput
                    while (true) {
                        var verticalLocked = false
                        var rejected = false
                        var velocityY = 0f
                        awaitPointerEventScope {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            dismissSettleJob?.cancel()
                            val gestureStartOffset = dismissOffset
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.count { it.pressed } > 1) rejected = true
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val delta = change.position - down.position
                                if (!verticalLocked && !rejected &&
                                    maxOf(abs(delta.x), abs(delta.y)) >= viewConfiguration.touchSlop
                                ) {
                                    if (abs(delta.y) > abs(delta.x) * 1.15f) verticalLocked = true
                                    else rejected = true
                                }
                                if (verticalLocked) {
                                    change.consume()
                                    dismissOffset = gestureStartOffset + delta.y
                                }
                                if (!change.pressed) {
                                    velocityY = tracker.calculateVelocity().y
                                    break
                                }
                            }
                        }
                        if (verticalLocked) {
                            val startOffset = dismissOffset
                            val dismissTarget = imageDismissTarget(
                                startOffset,
                                velocityY,
                                dismissDistancePx,
                                dismissVelocityPx,
                                viewportHeightPx
                            )
                            val shouldDismiss = dismissTarget != null
                            val target = dismissTarget ?: 0f
                            dismissSettleJob = viewerScope.launch {
                                Animatable(startOffset).animateTo(
                                    target,
                                    animationSpec = if (shouldDismiss) tween(145) else spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    initialVelocity = velocityY
                                ) { dismissOffset = value }
                                if (shouldDismiss) onClose()
                            }
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = currentScale <= 1.01f,
                key = { images[it].id }
            ) { page ->
                val attachment = images[page]
                ZoomableImagePage(
                    attachment = attachment,
                    attachmentManager = attachmentManager,
                    onClose = onClose,
                    onScaleChanged = { imageScales[attachment.id] = it },
                    imageModifier = if (attachment.id == sharedImageId) sharedImageModifier else Modifier
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(12.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f))
            ) {
                Icon(
                    Icons.Default.Close,
                    stringResource(R.string.close),
                    Modifier.size(26.dp),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    attachment: ChatAttachment,
    attachmentManager: AttachmentManager,
    onClose: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    imageModifier: Modifier
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.id) {
        value = withContext(Dispatchers.IO) { attachmentManager.decodePreview(attachment.id, 2_560) }
    }
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var offset by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale) { onScaleChanged(scale) }

    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded == null) {
            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            return@BoxWithConstraints
        }
        val imageAspect = loaded.width.toFloat() / loaded.height.toFloat().coerceAtLeast(1f)
        val viewportAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val fittedWidth = if (viewportAspect > imageAspect) maxHeight * imageAspect else maxWidth
        val fittedHeight = if (viewportAspect > imageAspect) maxHeight else maxWidth / imageAspect
        val fittedWidthPx = with(LocalDensity.current) { fittedWidth.toPx() }
        val fittedHeightPx = with(LocalDensity.current) { fittedHeight.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(attachment.id, fittedWidthPx, fittedHeightPx) {
                    detectTapGestures { position ->
                        val left = (size.width - fittedWidthPx) / 2f
                        val top = (size.height - fittedHeightPx) / 2f
                        if (position.x < left || position.x > left + fittedWidthPx ||
                            position.y < top || position.y > top + fittedHeightPx
                        ) onClose()
                    }
                }
        )
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = stringResource(R.string.image_attachment, attachment.name),
            modifier = Modifier
                .width(fittedWidth)
                .height(fittedHeight)
                .then(imageModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(attachment.id) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    })
                }
                .pointerInput(attachment.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val handlingTransform = event.changes.count { it.pressed } > 1 || scale > 1.01f
                            if (handlingTransform) {
                                val nextScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                offset = if (nextScale <= 1.01f) Offset.Zero else offset + event.calculatePan()
                                scale = nextScale
                                event.changes.forEach { it.consume() }
                            }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                },
            contentScale = ContentScale.Fit
        )
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

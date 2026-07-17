package com.example.privatevault.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.local.ChatBubblePalette
import com.example.privatevault.data.local.ThemePreference
import com.example.privatevault.BuildConfig
import com.example.privatevault.R
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.network.BackendRegistrationState
import com.example.privatevault.network.AppUpdate
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.ui.screen.chat.ChatScreen
import com.example.privatevault.ui.screen.chat.ChatViewModel
import com.example.privatevault.ui.screen.onboarding.OnboardingScreen
import com.example.privatevault.ui.screen.onboarding.OnboardingViewModel
import com.example.privatevault.ui.screen.pairing.PairingScreen
import com.example.privatevault.ui.screen.pairing.PairingViewModel
import com.example.privatevault.ui.screen.settings.SettingsScreen
import com.example.privatevault.ui.screen.storage.StorageBrowserScreen
import com.example.privatevault.ui.screen.storage.StorageBrowserViewModel
import com.example.privatevault.ui.theme.ProvideChatBubbleColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PrivateVaultApp(
    settingsStore: SettingsStore,
    chatRepository: ChatRepository,
    storageRepository: StorageRepository,
    pairingViewModelFactory: () -> PairingViewModel,
    registrationState: StateFlow<BackendRegistrationState>,
    onStorageSharingChanged: (Boolean) -> Unit,
    onPairingCodeRotated: () -> Unit,
    onRetryRegistration: () -> Unit,
    availableUpdate: StateFlow<AppUpdate?>,
    onDismissUpdate: (AppUpdate) -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    attachmentManager: AttachmentManager,
    onAttachFile: suspend (Uri) -> Result<ChatAttachment>,
    onBackupNow: suspend () -> Result<String>,
    onResetDebugKey: (String) -> Result<Unit>,
    themePreference: ThemePreference,
    onThemePreferenceChanged: (ThemePreference) -> Unit,
    onChatVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val attachmentFailedMessage = stringResource(R.string.attachment_failed)
    val scope = rememberCoroutineScope()
    var pendingAttachments by remember { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    val onboardingComplete: Boolean? by settingsStore.onboardingComplete.collectAsState(initial = null)
    val chatBubblePalette: ChatBubblePalette by settingsStore.chatBubblePalette.collectAsState(
        initial = ChatBubblePalette.Lavender
    )
    var destination by rememberSaveable { mutableStateOf(MainDestination.Chat) }

    val onboardingViewModel = pocViewModel { OnboardingViewModel(settingsStore) }
    val chatViewModel = pocViewModel { ChatViewModel(chatRepository) }
    val storageViewModel = pocViewModel { StorageBrowserViewModel(storageRepository) }
    val pairingViewModel = pocViewModel(pairingViewModelFactory)
    val pairingCode by pairingViewModel.pairingCode.collectAsState()
    val pairingAvailable by pairingViewModel.pairingAvailable.collectAsState()
    val backendRegistration by registrationState.collectAsState()
    val update by availableUpdate.collectAsState()
    val appReady = onboardingComplete == true || BuildConfig.LOCAL_ONLY
    val chatSelected = appReady && destination == MainDestination.Chat

    BackHandler(enabled = appReady && destination != MainDestination.Chat) {
        destination = MainDestination.Chat
    }

    DisposableEffect(chatSelected) {
        onChatVisibilityChanged(chatSelected)
        onDispose {
            if (chatSelected) onChatVisibilityChanged(false)
        }
    }
    val completeOnboarding: (Boolean) -> Unit = { granted ->
        onboardingViewModel.complete(granted) {
            onStorageSharingChanged(granted)
            destination = MainDestination.Chat
        }
    }

    val manageAllFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = hasStorageAccess(context)
        completeOnboarding(granted)
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        completeOnboarding(granted)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val attached = mutableListOf<ChatAttachment>()
                var firstError: Throwable? = null
                uris.forEach { uri ->
                    onAttachFile(uri)
                        .onSuccess(attached::add)
                        .onFailure { if (firstError == null) firstError = it }
                }
                if (attached.isNotEmpty()) {
                    pendingAttachments = (pendingAttachments + attached).distinctBy(ChatAttachment::id)
                }
                firstError?.let { error ->
                    Toast.makeText(
                        context,
                        error.message ?: attachmentFailedMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(onboardingComplete, BuildConfig.LOCAL_ONLY) {
        if (BuildConfig.LOCAL_ONLY && onboardingComplete == false) {
            settingsStore.completeOnboarding(storagePermissionGranted = false)
        } else if (onboardingComplete == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (onboardingComplete == false && !BuildConfig.LOCAL_ONLY) {
        OnboardingScreen(
            onRequestStorage = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { manageAllFilesLauncher.launch(intent) }
                        .onFailure {
                            manageAllFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                } else {
                    legacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            },
            modifier = modifier
        )
    } else if (appReady) {
        ProvideChatBubbleColors(chatBubblePalette) {
            when (destination) {
            MainDestination.Chat -> ChatScreen(
                viewModel = chatViewModel,
                pairingCode = pairingCode,
                pairingAvailable = pairingAvailable && !BuildConfig.LOCAL_ONLY,
                localOnly = BuildConfig.LOCAL_ONLY,
                registrationState = backendRegistration,
                onRetryRegistration = onRetryRegistration,
                onOpenSettings = { destination = MainDestination.Settings },
                onAttachFiles = { attachmentLauncher.launch(arrayOf("*/*")) },
                pendingAttachments = pendingAttachments,
                onRemovePendingAttachment = { attachmentId ->
                    pendingAttachments = pendingAttachments.filterNot { it.id == attachmentId }
                },
                onSendAttachments = { attachments, caption, replyToMessageId ->
                    chatViewModel.sendAttachments(attachments, caption, replyToMessageId).also { result ->
                        if (result.isSuccess) pendingAttachments = emptyList()
                    }
                },
                attachmentManager = attachmentManager,
                modifier = modifier
            )
            MainDestination.Storage -> StorageBrowserScreen(
                viewModel = storageViewModel,
                onClose = { destination = MainDestination.Chat },
                modifier = modifier
            )
            MainDestination.Settings -> SettingsScreen(
                onBack = { destination = MainDestination.Chat },
                localOnly = BuildConfig.LOCAL_ONLY,
                onOpenPairing = {
                    pairingViewModel.refresh()
                    destination = MainDestination.Pairing
                },
                onBackupNow = onBackupNow,
                onResetDebugKey = onResetDebugKey,
                themePreference = themePreference,
                onThemePreferenceChanged = onThemePreferenceChanged,
                chatBubblePalette = chatBubblePalette,
                onChatBubblePaletteChanged = { palette ->
                    scope.launch { settingsStore.setChatBubblePalette(palette) }
                },
                modifier = modifier
            )
            MainDestination.Pairing -> PairingScreen(
                viewModel = pairingViewModel,
                onBack = { destination = MainDestination.Chat },
                onCodeRotated = onPairingCodeRotated,
                registrationState = backendRegistration,
                onRetryRegistration = onRetryRegistration,
                modifier = modifier
            )
            }
        }
    }

    update?.let { available ->
        AlertDialog(
            onDismissRequest = { onDismissUpdate(available) },
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Text(stringResource(R.string.update_ready_body, available.version))
            },
            confirmButton = {
                TextButton(onClick = { onDownloadUpdate(available) }) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissUpdate(available) }) {
                    Text(stringResource(R.string.later))
                }
            }
        )
    }
}

@Composable
private inline fun <reified VM : ViewModel> pocViewModel(crossinline factory: () -> VM): VM {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
        }
    )
}

private fun hasStorageAccess(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

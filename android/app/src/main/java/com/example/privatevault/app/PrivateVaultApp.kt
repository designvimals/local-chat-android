package com.example.privatevault.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.network.BackendRegistrationState
import com.example.privatevault.network.AppUpdate
import com.example.privatevault.ui.screen.chat.ChatScreen
import com.example.privatevault.ui.screen.chat.ChatViewModel
import com.example.privatevault.ui.screen.onboarding.OnboardingScreen
import com.example.privatevault.ui.screen.onboarding.OnboardingViewModel
import com.example.privatevault.ui.screen.pairing.PairingScreen
import com.example.privatevault.ui.screen.pairing.PairingViewModel
import com.example.privatevault.ui.screen.settings.SettingsScreen
import com.example.privatevault.ui.screen.settings.SettingsViewModel
import com.example.privatevault.ui.screen.storage.StorageBrowserScreen
import com.example.privatevault.ui.screen.storage.StorageBrowserViewModel
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onboardingComplete by settingsStore.onboardingComplete.collectAsState(initial = false)
    var destination by rememberSaveable { mutableStateOf(MainDestination.Chat) }
    var onboardingPage by rememberSaveable { mutableStateOf(OnboardingPage.Permission) }
    var permissionGranted by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val onboardingViewModel = pocViewModel { OnboardingViewModel(settingsStore) }
    val chatViewModel = pocViewModel { ChatViewModel(chatRepository) }
    val storageViewModel = pocViewModel { StorageBrowserViewModel(storageRepository) }
    val settingsViewModel = pocViewModel { SettingsViewModel(settingsStore) }
    val pairingViewModel = pocViewModel(pairingViewModelFactory)
    val pairingCode by pairingViewModel.pairingCode.collectAsState()
    val pairingAvailable by pairingViewModel.pairingAvailable.collectAsState()
    val backendRegistration by registrationState.collectAsState()
    val update by availableUpdate.collectAsState()

    val manageAllFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val granted = hasStorageAccess(context)
        permissionGranted = granted
        onboardingPage = OnboardingPage.Result
        scope.launch { settingsStore.setStoragePermissionGranted(granted) }
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        onboardingPage = OnboardingPage.Result
        scope.launch { settingsStore.setStoragePermissionGranted(granted) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (!onboardingComplete) {
        OnboardingScreen(
            page = onboardingPage,
            permissionGranted = permissionGranted,
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
            onStartChat = {
                val granted = permissionGranted == true
                onboardingViewModel.complete(granted) {
                    onStorageSharingChanged(granted)
                    destination = MainDestination.Chat
                }
            },
            modifier = modifier
        )
    } else {
        when (destination) {
            MainDestination.Chat -> ChatScreen(
                viewModel = chatViewModel,
                pairingCode = pairingCode,
                pairingAvailable = pairingAvailable,
                registrationState = backendRegistration,
                onRetryRegistration = onRetryRegistration,
                onOpenStorage = { destination = MainDestination.Storage },
                onOpenSettings = { destination = MainDestination.Settings },
                modifier = modifier
            )
            MainDestination.Storage -> StorageBrowserScreen(
                viewModel = storageViewModel,
                onClose = { destination = MainDestination.Chat },
                modifier = modifier
            )
            MainDestination.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { destination = MainDestination.Chat },
                onOpenPairing = {
                    pairingViewModel.refresh()
                    destination = MainDestination.Pairing
                },
                onSharingChanged = onStorageSharingChanged,
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

    update?.let { available ->
        AlertDialog(
            onDismissRequest = { onDismissUpdate(available) },
            title = { Text("Update available") },
            text = {
                Text("Between ${available.version} is ready. Download the APK from GitHub and install it over this app. Your local messages will stay on this phone.")
            },
            confirmButton = {
                TextButton(onClick = { onDownloadUpdate(available) }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissUpdate(available) }) {
                    Text("Later")
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

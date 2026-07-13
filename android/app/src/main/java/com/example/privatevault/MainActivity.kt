package com.example.privatevault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.privatevault.app.PrivateVaultApp
import com.example.privatevault.app.PrivateVaultApplication
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.backup.ChatBackupManager
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.network.BackendClient
import com.example.privatevault.network.PeerRelayClient
import com.example.privatevault.network.AppUpdate
import com.example.privatevault.network.GithubUpdateChecker
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.service.StorageSessionNotifier
import com.example.privatevault.security.AppLockManager
import com.example.privatevault.ui.lock.AppLockGate
import com.example.privatevault.ui.screen.pairing.PairingViewModel
import com.example.privatevault.ui.theme.PrivateVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var tokenStore: TokenStore
    private lateinit var backendClient: BackendClient
    private lateinit var peerRelayClient: PeerRelayClient
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var chatRepository: ChatRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var notifier: StorageSessionNotifier
    private lateinit var appLockManager: AppLockManager
    private val availableUpdate = MutableStateFlow<AppUpdate?>(null)
    private var dismissedUpdateVersion: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val runtime = (application as PrivateVaultApplication).runtime
        appLockManager = runtime.appLockManager
        settingsStore = runtime.settingsStore
        tokenStore = runtime.tokenStore
        attachmentManager = runtime.attachmentManager
        notifier = StorageSessionNotifier(applicationContext)
        chatRepository = runtime.chatRepository
        storageRepository = runtime.storageRepository
        backendClient = runtime.backendClient
        peerRelayClient = runtime.peerRelayClient

        setContent {
            PrivateVaultTheme {
                AppLockGate(manager = appLockManager) {
                    PrivateVaultApp(
                        settingsStore = settingsStore,
                        chatRepository = chatRepository,
                        storageRepository = storageRepository,
                        pairingViewModelFactory = { PairingViewModel(tokenStore, peerRelayClient, ::restartPeerRelay) },
                        registrationState = backendClient.registrationState,
                        onStorageSharingChanged = ::applySharingState,
                        onPairingCodeRotated = ::restartRelay,
                        onRetryRegistration = ::restartRelay,
                        availableUpdate = availableUpdate,
                        onDismissUpdate = ::dismissUpdate,
                        onDownloadUpdate = ::downloadUpdate,
                        attachmentManager = attachmentManager,
                        onAttachFile = ::attachFile,
                        onBackupNow = ::backupNow,
                        onResetDebugKey = appLockManager::setDebugKey
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockManager.onAppForegrounded()
        lifecycleScope.launch {
            val storageAccessGranted = hasStorageAccess(this@MainActivity)
            settingsStore.setStoragePermissionGranted(storageAccessGranted)
            if (settingsStore.onboardingComplete.first()) {
                applySharingState(settingsStore.storageSharingEnabled.first() && storageAccessGranted)
            }
        }
        lifecycleScope.launch {
            val update = GithubUpdateChecker.findAvailableUpdate()
            availableUpdate.value = update?.takeUnless { it.version == dismissedUpdateVersion }
        }
    }

    override fun onStop() {
        appLockManager.onAppBackgrounded()
        super.onStop()
    }

    private fun applySharingState(enabled: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val storageAvailable = enabled && hasStorageAccess(this@MainActivity)
                notifier.markAvailable(storageAvailable)
            }
        }
    }

    private fun restartRelay() {
        lifecycleScope.launch {
            backendClient.restartConnection()
            val storageAvailable = settingsStore.storageSharingEnabled.first() &&
                hasStorageAccess(this@MainActivity)
            notifier.markAvailable(storageAvailable)
        }
    }

    private fun restartPeerRelay() {
        lifecycleScope.launch {
            peerRelayClient.restartConnection()
        }
    }

    private suspend fun attachFile(uri: Uri): Result<ChatAttachment> = withContext(Dispatchers.IO) {
        runCatching { attachmentManager.registerOriginal(uri) }
    }

    private suspend fun backupNow(): Result<String> = withContext(Dispatchers.IO) {
        runCatching { ChatBackupManager(applicationContext).createBackup("manual").absolutePath }
    }

    private fun dismissUpdate(update: AppUpdate) {
        dismissedUpdateVersion = update.version
        availableUpdate.value = null
    }

    private fun downloadUpdate(update: AppUpdate) {
        dismissUpdate(update)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
        }
    }

    companion object {
        fun hasStorageAccess(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
    }
}

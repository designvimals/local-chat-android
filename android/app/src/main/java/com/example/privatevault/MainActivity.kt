package com.example.privatevault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.metrics.performance.JankStats
import androidx.compose.runtime.getValue
import com.example.privatevault.app.PrivateVaultApp
import com.example.privatevault.app.PrivateVaultApplication
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.backup.ChatBackupManager
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.local.ThemePreference
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.network.BackendClient
import com.example.privatevault.network.AppConfigRepository
import com.example.privatevault.network.PeerRelayClient
import com.example.privatevault.network.AppUpdate
import com.example.privatevault.network.GithubUpdateChecker
import com.example.privatevault.network.availableUpdate
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.server.LocalServerManager
import com.example.privatevault.service.StorageSessionNotifier
import com.example.privatevault.security.AppLockManager
import com.example.privatevault.ui.lock.AppLockGate
import com.example.privatevault.ui.screen.pairing.PairingViewModel
import com.example.privatevault.ui.theme.PrivateVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: SettingsStore
    private lateinit var tokenStore: TokenStore
    private lateinit var backendClient: BackendClient
    private lateinit var appConfigRepository: AppConfigRepository
    private lateinit var peerRelayClient: PeerRelayClient
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var chatRepository: ChatRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var localServerManager: LocalServerManager
    private lateinit var notifier: StorageSessionNotifier
    private lateinit var appLockManager: AppLockManager
    private val availableUpdate = MutableStateFlow<AppUpdate?>(null)
    private var dismissedUpdateVersion: String? = null
    private var jankStats: JankStats? = null
    private var visibleRelayJob: Job? = null
    private var visiblePeerRelayJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.DEBUG) {
            jankStats = JankStats.createAndTrack(window) { frameData ->
                if (frameData.isJank) {
                    Log.w(JANK_LOG_TAG, "Janky frame: ${frameData.frameDurationUiNanos / 1_000_000f} ms")
                }
            }
        }

        val runtime = (application as PrivateVaultApplication).runtime
        appLockManager = runtime.appLockManager
        settingsStore = runtime.settingsStore
        tokenStore = runtime.tokenStore
        attachmentManager = runtime.attachmentManager
        notifier = StorageSessionNotifier(applicationContext)
        chatRepository = runtime.chatRepository
        storageRepository = runtime.storageRepository
        localServerManager = runtime.localServerManager
        backendClient = runtime.backendClient
        appConfigRepository = runtime.appConfigRepository
        peerRelayClient = runtime.peerRelayClient

        setContent {
            val themePreference by settingsStore.themePreference.collectAsStateWithLifecycle(
                initialValue = ThemePreference.System
            )
            PrivateVaultTheme(themePreference = themePreference) {
                AppLockGate(manager = appLockManager) {
                    PrivateVaultApp(
                        settingsStore = settingsStore,
                        chatRepository = chatRepository,
                        storageRepository = storageRepository,
                        pairingViewModelFactory = { PairingViewModel(tokenStore, peerRelayClient, ::restartPeerRelay) },
                        registrationState = backendClient.registrationState,
                        remoteAppConfig = appConfigRepository.config,
                        onStorageSharingChanged = ::applySharingState,
                        onPairingCodeRotated = ::restartRelay,
                        onRetryRegistration = ::restartRelay,
                        availableUpdate = availableUpdate,
                        onDismissUpdate = ::dismissUpdate,
                        onDownloadUpdate = ::downloadUpdate,
                        attachmentManager = attachmentManager,
                        onAttachFile = ::attachFile,
                        onBackupNow = ::backupNow,
                        onResetDebugKey = appLockManager::setDebugKey,
                        themePreference = themePreference,
                        onThemePreferenceChanged = { preference ->
                            lifecycleScope.launch { settingsStore.setThemePreference(preference) }
                        },
                        onChatVisibilityChanged = runtime.chatVisibilityTracker::setChatDestinationSelected
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as PrivateVaultApplication).runtime.chatVisibilityTracker.setActivityForeground(true)
        appLockManager.onAppForegrounded()
        lifecycleScope.launch {
            if (BuildConfig.LOCAL_ONLY) {
                if (!settingsStore.onboardingComplete.first()) {
                    settingsStore.completeOnboarding(storagePermissionGranted = false)
                }
                notifier.markInactive()
                return@launch
            }
            val storageAccessGranted = hasStorageAccess(this@MainActivity)
            settingsStore.setStoragePermissionGranted(storageAccessGranted)
            if (settingsStore.onboardingComplete.first()) {
                applySharingState(settingsStore.storageSharingEnabled.first() && storageAccessGranted)
            }
        }
        if (!BuildConfig.LOCAL_ONLY) lifecycleScope.launch {
            val previousRelayUrl = appConfigRepository.current.relayBaseUrl
            val refreshedConfig = appConfigRepository.refresh()
            if (refreshedConfig.relayBaseUrl != previousRelayUrl) {
                backendClient.restartConnection()
                peerRelayClient.restartConnection()
            }
            val update = refreshedConfig.availableUpdate(BuildConfig.VERSION_NAME)
                ?: GithubUpdateChecker.findAvailableUpdate()
            availableUpdate.value = update?.takeUnless { it.version == dismissedUpdateVersion }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::appLockManager.isInitialized) appLockManager.onUserInteraction()
    }

    override fun onStop() {
        (application as PrivateVaultApplication).runtime.chatVisibilityTracker.setActivityForeground(false)
        stopVisibleConnectionLoops()
        notifier.markInactive()
        lifecycleScope.launch(Dispatchers.IO) {
            localServerManager.stop()
        }
        appLockManager.onAppBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        jankStats?.isTrackingEnabled = false
        jankStats = null
        super.onDestroy()
    }

    private fun applySharingState(enabled: Boolean) {
        if (BuildConfig.LOCAL_ONLY) {
            notifier.markInactive()
            stopVisibleConnectionLoops()
            return
        }
        lifecycleScope.launch {
            notifier.markInactive()
            val storageAvailable = withContext(Dispatchers.IO) {
                enabled &&
                    appConfigRepository.current.features.fileSharing &&
                    hasStorageAccess(this@MainActivity)
            }
            withContext(Dispatchers.IO) {
                if (storageAvailable) {
                    localServerManager.start(tokenStore.getAccessToken())
                } else {
                    localServerManager.stop()
                }
                backendClient.updateStorageSharing(storageAvailable)
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startVisibleConnectionLoops()
            }
        }
    }

    private fun restartRelay() {
        if (BuildConfig.LOCAL_ONLY) return
        lifecycleScope.launch {
            backendClient.restartConnection()
        }
    }

    private fun restartPeerRelay() {
        if (BuildConfig.LOCAL_ONLY) return
        lifecycleScope.launch {
            peerRelayClient.restartConnection()
        }
    }

    private fun startVisibleConnectionLoops() {
        if (BuildConfig.LOCAL_ONLY) return
        if (visibleRelayJob?.isActive != true) {
            visibleRelayJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(VISIBLE_CONNECTION_START_DELAY_MS)
                while (isActive) {
                    val storageAvailable = settingsStore.storageSharingEnabled.first() &&
                        settingsStore.storagePermissionGranted.first() &&
                        appConfigRepository.current.features.fileSharing
                    backendClient.connectRelay(storageSharingEnabled = storageAvailable)
                    delay(appConfigRepository.current.timing.connectionRetryMillis)
                }
            }
        }
        if (visiblePeerRelayJob?.isActive != true) {
            visiblePeerRelayJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(VISIBLE_CONNECTION_START_DELAY_MS)
                while (isActive) {
                    val connection = tokenStore.getPeerConnection()
                    if (connection == null) {
                        delay(appConfigRepository.current.timing.connectionRetryMillis)
                        continue
                    }
                    peerRelayClient.connectPeer(connection)
                    delay(appConfigRepository.current.timing.connectionRetryMillis)
                }
            }
        }
    }

    private fun stopVisibleConnectionLoops() {
        visibleRelayJob?.cancel()
        visibleRelayJob = null
        visiblePeerRelayJob?.cancel()
        visiblePeerRelayJob = null
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
        private const val JANK_LOG_TAG = "ChatFrameTiming"
        private const val VISIBLE_CONNECTION_START_DELAY_MS = 200L

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

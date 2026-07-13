package com.example.privatevault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.privatevault.app.PrivateVaultApp
import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.DeviceRepository
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.network.BackendClient
import com.example.privatevault.network.AppUpdate
import com.example.privatevault.network.GithubUpdateChecker
import com.example.privatevault.server.LocalServerManager
import com.example.privatevault.server.PathResolver
import com.example.privatevault.service.StorageSessionNotifier
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
    private lateinit var serverManager: LocalServerManager
    private lateinit var backendClient: BackendClient
    private lateinit var chatRepository: ChatRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var notifier: StorageSessionNotifier
    private var registrationJob: Job? = null
    private val availableUpdate = MutableStateFlow<AppUpdate?>(null)
    private var dismissedUpdateVersion: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(applicationContext)
        tokenStore = TokenStore(applicationContext)
        val messageStore = MessageStore(applicationContext, tokenStore)
        val deviceRepository = DeviceRepository(tokenStore)
        val pathResolver = PathResolver()
        notifier = StorageSessionNotifier(applicationContext)

        chatRepository = ChatRepository(messageStore, tokenStore)
        storageRepository = StorageRepository(pathResolver)
        serverManager = LocalServerManager(
            settingsStore = settingsStore,
            chatRepository = chatRepository,
            pathResolver = pathResolver,
            deviceRepository = deviceRepository,
            notifier = notifier
        )
        backendClient = BackendClient(
            tokenStore = tokenStore,
            deviceRepository = deviceRepository,
            chatRepository = chatRepository,
            pathResolver = pathResolver,
            settingsStore = settingsStore
        )

        setContent {
            PrivateVaultTheme {
                PrivateVaultApp(
                    settingsStore = settingsStore,
                    chatRepository = chatRepository,
                    storageRepository = storageRepository,
                    pairingViewModelFactory = { PairingViewModel(tokenStore) },
                    registrationState = backendClient.registrationState,
                    onStorageSharingChanged = ::applySharingState,
                    onPairingCodeRotated = ::restartRelay,
                    onRetryRegistration = ::restartRelay,
                    availableUpdate = availableUpdate,
                    onDismissUpdate = ::dismissUpdate,
                    onDownloadUpdate = ::downloadUpdate
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            settingsStore.setStoragePermissionGranted(hasStorageAccess(this@MainActivity))
            if (settingsStore.onboardingComplete.first()) {
                val shouldShare = settingsStore.storageSharingEnabled.first() && hasStorageAccess(this@MainActivity)
                applySharingState(shouldShare)
            }
        }
        lifecycleScope.launch {
            val update = GithubUpdateChecker.findAvailableUpdate()
            availableUpdate.value = update?.takeUnless { it.version == dismissedUpdateVersion }
        }
    }

    private fun applySharingState(enabled: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val storageAvailable = enabled && hasStorageAccess(this@MainActivity)
                serverManager.start(tokenStore.getAccessToken())
                notifier.markAvailable(storageAvailable)
                backendClient.updateStorageSharing(storageAvailable)
            }
            startRelayLoop()
        }
    }

    private fun restartRelay() {
        lifecycleScope.launch {
            backendClient.restartConnection()
            startRelayLoop()
        }
    }

    private fun startRelayLoop() {
        if (registrationJob?.isActive == true) return
        registrationJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val storageAvailable = settingsStore.storageSharingEnabled.first() &&
                    hasStorageAccess(this@MainActivity)
                backendClient.connectRelay(storageSharingEnabled = storageAvailable)
                delay(2_000)
            }
        }
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

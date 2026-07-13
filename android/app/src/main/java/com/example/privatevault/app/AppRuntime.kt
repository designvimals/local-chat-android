package com.example.privatevault.app

import android.content.Context
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.data.local.MessageStore
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.DeviceRepository
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.network.BackendClient
import com.example.privatevault.network.PeerRelayClient
import com.example.privatevault.server.LocalServerManager
import com.example.privatevault.server.PathResolver
import com.example.privatevault.security.AppLockManager

/** Process-wide graph shared by the UI and the foreground connection service. */
class AppRuntime(context: Context) {
    val appLockManager = AppLockManager(context)
    val settingsStore = SettingsStore(context)
    val tokenStore = TokenStore(context)
    val attachmentManager = AttachmentManager(context)
    val deviceRepository = DeviceRepository(tokenStore)
    val pathResolver = PathResolver()
    val chatRepository = ChatRepository(MessageStore(context, tokenStore), tokenStore)
    val storageRepository = StorageRepository(pathResolver)
    val localServerManager = LocalServerManager(
        settingsStore = settingsStore,
        chatRepository = chatRepository,
        pathResolver = pathResolver,
        deviceRepository = deviceRepository
    )
    val backendClient = BackendClient(
        tokenStore = tokenStore,
        deviceRepository = deviceRepository,
        chatRepository = chatRepository,
        pathResolver = pathResolver,
        settingsStore = settingsStore,
        attachmentManager = attachmentManager
    )
    val peerRelayClient = PeerRelayClient(
        tokenStore = tokenStore,
        deviceRepository = deviceRepository,
        chatRepository = chatRepository,
        attachmentManager = attachmentManager
    )
}

package com.example.privatevault.service

internal enum class BackgroundConnectionMode(val storageAvailable: Boolean) {
    ChatAndFiles(storageAvailable = true),
    ChatOnly(storageAvailable = false)
}

internal fun resolveBackgroundConnectionMode(
    storageSharingEnabled: Boolean,
    storagePermissionGranted: Boolean,
    remoteFileSharingEnabled: Boolean
): BackgroundConnectionMode {
    return if (storageSharingEnabled && storagePermissionGranted && remoteFileSharingEnabled) {
        BackgroundConnectionMode.ChatAndFiles
    } else {
        BackgroundConnectionMode.ChatOnly
    }
}

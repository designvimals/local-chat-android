package com.example.privatevault.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundConnectionModeTest {
    @Test
    fun `chat and files requires the setting permission and remote feature`() {
        assertEquals(
            BackgroundConnectionMode.ChatAndFiles,
            resolveBackgroundConnectionMode(
                storageSharingEnabled = true,
                storagePermissionGranted = true,
                remoteFileSharingEnabled = true
            )
        )
    }

    @Test
    fun `disabling any file prerequisite falls back to chat only`() {
        val states = listOf(
            Triple(false, true, true),
            Triple(true, false, true),
            Triple(true, true, false),
            Triple(false, false, false)
        )

        states.forEach { (enabled, permission, remoteFeature) ->
            assertEquals(
                BackgroundConnectionMode.ChatOnly,
                resolveBackgroundConnectionMode(
                    storageSharingEnabled = enabled,
                    storagePermissionGranted = permission,
                    remoteFileSharingEnabled = remoteFeature
                )
            )
        }
    }
}

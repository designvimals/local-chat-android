package com.example.privatevault.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OneTimeFullResetTest {
    @Test
    fun existingDataOnResetReleaseRequestsAndroidClearStorage() {
        assertEquals(
            FullResetAction.RequestClear,
            decideFullResetAction(
                versionCode = OneTimeFullReset.RESET_VERSION_CODE,
                resetCompleted = false,
                legacyAppStateExists = true
            )
        )
    }

    @Test
    fun firstCleanLaunchWritesCompletionMarker() {
        assertEquals(
            FullResetAction.MarkComplete,
            decideFullResetAction(
                versionCode = OneTimeFullReset.RESET_VERSION_CODE,
                resetCompleted = false,
                legacyAppStateExists = false
            )
        )
    }

    @Test
    fun completedResetNeverRunsAgain() {
        assertEquals(
            FullResetAction.None,
            decideFullResetAction(
                versionCode = OneTimeFullReset.RESET_VERSION_CODE,
                resetCompleted = true,
                legacyAppStateExists = true
            )
        )
    }

    @Test
    fun laterReleaseStillResetsUsersWhoSkippedTheResetRelease() {
        assertEquals(
            FullResetAction.RequestClear,
            decideFullResetAction(
                versionCode = OneTimeFullReset.RESET_VERSION_CODE + 1,
                resetCompleted = false,
                legacyAppStateExists = true
            )
        )
    }
}

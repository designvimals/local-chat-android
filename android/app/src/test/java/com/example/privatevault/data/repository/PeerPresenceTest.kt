package com.example.privatevault.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerPresenceTest {
    @Test
    fun onlineObservationRecordsTheCurrentTime() {
        val presence = reducePeerPresence(PeerPresence(), isOnline = true, observedAtMillis = 1_000L)

        assertTrue(presence.isOnline)
        assertEquals(1_000L, presence.lastSeenAtMillis)
    }

    @Test
    fun disconnectRecordsWhenThePeerWasLastSeen() {
        val presence = reducePeerPresence(
            PeerPresence(isOnline = true, lastSeenAtMillis = 1_000L),
            isOnline = false,
            observedAtMillis = 2_000L
        )

        assertFalse(presence.isOnline)
        assertEquals(2_000L, presence.lastSeenAtMillis)
    }

    @Test
    fun repeatedOfflineUpdatesPreserveTheKnownLastSeenTime() {
        val presence = reducePeerPresence(
            PeerPresence(isOnline = false, lastSeenAtMillis = 1_000L),
            isOnline = false,
            observedAtMillis = 2_000L
        )

        assertFalse(presence.isOnline)
        assertEquals(1_000L, presence.lastSeenAtMillis)
    }
}

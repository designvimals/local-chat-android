package com.example.privatevault.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSlotsTest {
    @Test
    fun webThenAndroidUsesEachSlotOnceAndClosesCode() {
        val afterWeb = emptyState().claim(PairingClientType.Web)
        assertTrue(afterWeb.hasOpenSlot)
        assertEquals(listOf("web"), afterWeb.claimedClientTypes())

        val afterAndroid = afterWeb.claim(PairingClientType.Android)
        assertFalse(afterAndroid.hasOpenSlot)
        assertEquals(listOf("web", "android"), afterAndroid.claimedClientTypes())
    }

    @Test
    fun AndroidThenWebAlsoClosesCode() {
        val afterAndroid = emptyState().claim(PairingClientType.Android)
        assertTrue(afterAndroid.hasOpenSlot)
        assertEquals(listOf("android"), afterAndroid.claimedClientTypes())

        assertFalse(afterAndroid.claim(PairingClientType.Web).hasOpenSlot)
    }

    @Test
    fun duplicateClaimIsIdempotentAndLeavesOtherSlotOpen() {
        val afterTwoWebClaims = emptyState()
            .claim(PairingClientType.Web)
            .claim(PairingClientType.Web)

        assertTrue(afterTwoWebClaims.hasOpenSlot)
        assertEquals(listOf("web"), afterTwoWebClaims.claimedClientTypes())
    }

    @Test
    fun legacyClaimedCodeStaysClosed() {
        val state = PairingSlotState.fromLegacyClaimed(true)
        assertFalse(state.hasOpenSlot)
        assertEquals(listOf("web", "android"), state.claimedClientTypes())
    }

    @Test
    fun missingClientTypeCanBeDetectedForConservativeFallback() {
        assertEquals(null, PairingClientType.fromWireName(null))
        assertEquals(null, PairingClientType.fromWireName("desktop"))
        assertEquals(PairingClientType.Android, PairingClientType.fromWireName("android"))
    }

    private fun emptyState() = PairingSlotState(webClaimed = false, androidClaimed = false)
}

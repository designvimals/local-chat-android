package com.example.privatevault.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveTextWaveTest {
    @Test
    fun `wave starts and finishes at rest`() {
        assertEquals(1f, letterWaveScale(0f, 0, 5), 0.0001f)
        assertEquals(1f, letterWaveScale(1f, 4, 5), 0.0001f)
    }

    @Test
    fun `wave activates letters in sequence`() {
        val earlyFirst = letterWaveScale(0.18f, 0, 5)
        val earlyLast = letterWaveScale(0.18f, 4, 5)
        val lateLast = letterWaveScale(0.8f, 4, 5)

        assertTrue(earlyFirst > 1f)
        assertEquals(1f, earlyLast, 0.0001f)
        assertTrue(lateLast > 1f)
    }

    @Test
    fun `wave pulse is visible but restrained`() {
        assertEquals(1.14f, letterWaveScale(0.225f, 0, 5), 0.001f)
    }

    @Test
    fun `graphemes keep combining characters together`() {
        assertEquals(listOf("A", "e\u0301", "B"), expressiveGraphemes("Ae\u0301B"))
    }
}

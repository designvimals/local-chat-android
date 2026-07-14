package com.example.privatevault.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiIcuTest {
    @Test fun androidIcuDetectsRequiredSequences() {
        listOf("🇮🇳", "1️⃣", "👍🏽", "👨‍👩‍👧‍👦", "❤️").forEach { emoji ->
            assertEquals(emoji, singleEmojiOrNull(emoji))
        }
        listOf("hello", "1", "👍👍", "❤️🔥").forEach { value ->
            assertNull(singleEmojiOrNull(value))
        }
    }
}

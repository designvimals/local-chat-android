package com.example.privatevault.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.regex.Pattern

class EmojiMessageTest {
    @Test fun detectsEmojiGraphemeSequences() {
        listOf("🇮🇳", "1️⃣", "👍🏽", "👨‍👩‍👧‍👦", "❤️").forEach { emoji ->
            assertEquals(emoji, detect(emoji))
        }
    }

    @Test fun rejectsTextAndMultipleEmoji() {
        listOf("hello", "1", "👍👍", "❤️🔥").forEach { value ->
            assertNull(detect(value))
        }
    }

    private fun detect(value: String): String? = singleEmojiOrNull(
        value,
        isSingleGrapheme = { text ->
            val matcher = Pattern.compile("\\X").matcher(text)
            matcher.find() && matcher.start() == 0 && matcher.end() == text.length && !matcher.find()
        },
        hasEmojiProperty = { codePoint ->
            codePoint in 0x1F000..0x1FAFF ||
                codePoint in 0x1F1E6..0x1F1FF ||
                codePoint in 0x2600..0x27BF ||
                codePoint in 0x30..0x39 || codePoint == 0x23 || codePoint == 0x2A
        }
    )
}

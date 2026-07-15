package com.example.privatevault.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.privatevault.data.local.ChatBubblePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBubbleColorsTest {
    @Test
    fun storedPaletteFallsBackSafely() {
        assertEquals(ChatBubblePalette.Ocean, ChatBubblePalette.fromStored("Ocean"))
        assertEquals(ChatBubblePalette.Lavender, ChatBubblePalette.fromStored("unknown"))
        assertEquals(ChatBubblePalette.Lavender, ChatBubblePalette.fromStored(null))
    }

    @Test
    fun everyPresetMaintainsReadableBubbleContrast() {
        ChatBubblePalette.entries.forEach { palette ->
            listOf(false, true).forEach { darkTheme ->
                val colors = palette.resolveChatBubbleColors(darkTheme)
                assertReadable(palette, darkTheme, "outgoing", colors.onOutgoing, colors.outgoing)
                assertReadable(palette, darkTheme, "incoming", colors.onIncoming, colors.incoming)
                val screenBackground = if (darkTheme) DarkBackground else LightBackground
                assertReadable(
                    palette,
                    darkTheme,
                    "expressive start",
                    colors.expressiveStart,
                    screenBackground
                )
                assertReadable(
                    palette,
                    darkTheme,
                    "expressive end",
                    colors.expressiveEnd,
                    screenBackground
                )
            }
        }
    }

    private fun assertReadable(
        palette: ChatBubblePalette,
        darkTheme: Boolean,
        side: String,
        foreground: Color,
        background: Color
    ) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue(
            "$palette ${if (darkTheme) "dark" else "light"} $side contrast was $ratio",
            ratio >= 4.5f
        )
    }
}

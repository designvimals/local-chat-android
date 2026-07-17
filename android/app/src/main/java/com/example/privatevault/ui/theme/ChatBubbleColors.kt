package com.example.privatevault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.privatevault.data.local.ChatBubblePalette

@Immutable
data class ChatBubbleColors(
    val outgoing: Color,
    val onOutgoing: Color,
    val incoming: Color,
    val onIncoming: Color,
    val expressiveStart: Color,
    val expressiveEnd: Color
) {
    val composerAdd: Color get() = expressiveEnd
    val onComposerAdd: Color get() = contrastingContentColor(composerAdd)
    val composerSend: Color get() = expressiveStart
    val onComposerSend: Color get() = contrastingContentColor(composerSend)
}

private fun contrastingContentColor(container: Color): Color {
    val luminance = container.luminance()
    val whiteContrast = 1.05f / (luminance + 0.05f)
    val blackContrast = (luminance + 0.05f) / 0.05f
    return if (whiteContrast >= blackContrast) Color.White else Color.Black
}

private val LavenderLight = ChatBubbleColors(
    outgoing = Color(0xFFE1E2FF),
    onOutgoing = Color(0xFF151958),
    incoming = Color(0xFFF3DAFF),
    onIncoming = Color(0xFF2E123F),
    expressiveStart = Color(0xFF555ACB),
    expressiveEnd = Color(0xFF735573)
)

val LocalChatBubbleColors = staticCompositionLocalOf { LavenderLight }

fun ChatBubblePalette.resolveChatBubbleColors(darkTheme: Boolean): ChatBubbleColors = when (this) {
    ChatBubblePalette.Lavender -> if (darkTheme) ChatBubbleColors(
        outgoing = Color(0xFF3C428B),
        onOutgoing = Color(0xFFE1E2FF),
        incoming = Color(0xFF5D3C6E),
        onIncoming = Color(0xFFF3DAFF),
        expressiveStart = Color(0xFFBEC2FF),
        expressiveEnd = Color(0xFFE2BBDD)
    ) else LavenderLight
    ChatBubblePalette.Ocean -> if (darkTheme) ChatBubbleColors(
        outgoing = Color(0xFF006A9B),
        onOutgoing = Color.White,
        incoming = Color(0xFF153345),
        onIncoming = Color(0xFFD9EFFF),
        expressiveStart = Color(0xFF8CD1FF),
        expressiveEnd = Color(0xFF83D6E9)
    ) else ChatBubbleColors(
        outgoing = Color(0xFF00639B),
        onOutgoing = Color.White,
        incoming = Color(0xFFD2E5F5),
        onIncoming = Color(0xFF0A344E),
        expressiveStart = Color(0xFF00639B),
        expressiveEnd = Color(0xFF315DA8)
    )
    ChatBubblePalette.Jade -> if (darkTheme) ChatBubbleColors(
        outgoing = Color(0xFF096B50),
        onOutgoing = Color.White,
        incoming = Color(0xFF19392F),
        onIncoming = Color(0xFFD5F6E7),
        expressiveStart = Color(0xFF75DDB7),
        expressiveEnd = Color(0xFFB8D77A)
    ) else ChatBubbleColors(
        outgoing = Color(0xFF006C4C),
        onOutgoing = Color.White,
        incoming = Color(0xFFD2EBDD),
        onIncoming = Color(0xFF10382C),
        expressiveStart = Color(0xFF006C4C),
        expressiveEnd = Color(0xFF4F5F00)
    )
    ChatBubblePalette.Coral -> if (darkTheme) ChatBubbleColors(
        outgoing = Color(0xFF9F4143),
        onOutgoing = Color.White,
        incoming = Color(0xFF462326),
        onIncoming = Color(0xFFFFDAD7),
        expressiveStart = Color(0xFFFFB4AC),
        expressiveEnd = Color(0xFFFFC080)
    ) else ChatBubbleColors(
        outgoing = Color(0xFFA53A3A),
        onOutgoing = Color.White,
        incoming = Color(0xFFF9DDDA),
        onIncoming = Color(0xFF4A1717),
        expressiveStart = Color(0xFFA53A3A),
        expressiveEnd = Color(0xFF8A4900)
    )
    ChatBubblePalette.Rose -> if (darkTheme) ChatBubbleColors(
        outgoing = Color(0xFF914061),
        onOutgoing = Color.White,
        incoming = Color(0xFF432432),
        onIncoming = Color(0xFFFFD9E7),
        expressiveStart = Color(0xFFFFB0CB),
        expressiveEnd = Color(0xFFD8B5FF)
    ) else ChatBubbleColors(
        outgoing = Color(0xFF9B3C68),
        onOutgoing = Color.White,
        incoming = Color(0xFFF5DDE7),
        onIncoming = Color(0xFF4A1630),
        expressiveStart = Color(0xFF9B3C68),
        expressiveEnd = Color(0xFF6F4A8E)
    )
    ChatBubblePalette.Amber -> if (darkTheme) ChatBubbleColors(
        outgoing = Color(0xFF735400),
        onOutgoing = Color.White,
        incoming = Color(0xFF3C3017),
        onIncoming = Color(0xFFFFE8AF),
        expressiveStart = Color(0xFFFFD26F),
        expressiveEnd = Color(0xFFFFB68A)
    ) else ChatBubbleColors(
        outgoing = Color(0xFF7A5600),
        onOutgoing = Color.White,
        incoming = Color(0xFFF4E3BC),
        onIncoming = Color(0xFF3D2B00),
        expressiveStart = Color(0xFF7A5600),
        expressiveEnd = Color(0xFF8C3F1D)
    )
}

@Composable
fun ProvideChatBubbleColors(
    palette: ChatBubblePalette,
    content: @Composable () -> Unit
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    CompositionLocalProvider(
        LocalChatBubbleColors provides palette.resolveChatBubbleColors(darkTheme),
        content = content
    )
}

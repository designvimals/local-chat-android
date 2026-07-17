package com.example.privatevault.ui.screen.chat

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

internal fun expressiveGraphemes(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val result = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    return result
}

/** A short transform-only pulse that travels from the first grapheme to the last. */
internal fun letterWaveScale(progress: Float, index: Int, count: Int): Float {
    if (count <= 0 || index !in 0 until count) return 1f
    val clampedProgress = progress.coerceIn(0f, 1f)
    val stagger = if (count == 1) 0f else index.toFloat() / (count - 1) * 0.55f
    val localProgress = ((clampedProgress - stagger) / 0.45f).coerceIn(0f, 1f)
    if (localProgress <= 0f || localProgress >= 1f) return 1f
    val pulse = sin(localProgress * PI).toFloat().coerceAtLeast(0f)
    return 1f + pulse * 0.14f
}

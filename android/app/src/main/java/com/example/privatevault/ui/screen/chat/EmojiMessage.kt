package com.example.privatevault.ui.screen.chat

import android.icu.lang.UCharacter
import android.icu.text.BreakIterator
import android.util.LruCache
import java.util.Locale

private const val NotEmoji = "\u0000"
// UProperty.EMOJI is the stable ICU property value 57, but Android's SDK did not
// expose the named constant until API 28. Using the value keeps API 26-27 safe.
private const val EmojiProperty = 57
private val emojiResultCache = LruCache<String, String>(256)

/** Returns the original text when it is exactly one emoji grapheme, otherwise null. */
fun singleEmojiOrNull(value: String): String? {
    synchronized(emojiResultCache) {
        emojiResultCache.get(value)?.let { return if (it == NotEmoji) null else it }
    }
    val result = singleEmojiOrNull(
        value = value,
        isSingleGrapheme = { text ->
            val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
            iterator.setText(text)
            iterator.first() == 0 && iterator.next() == text.length && iterator.next() == BreakIterator.DONE
        },
        hasEmojiProperty = { codePoint -> UCharacter.hasBinaryProperty(codePoint, EmojiProperty) }
    )
    synchronized(emojiResultCache) {
        emojiResultCache.put(value, result ?: NotEmoji)
    }
    return result
}

internal fun singleEmojiOrNull(
    value: String,
    isSingleGrapheme: (String) -> Boolean,
    hasEmojiProperty: (Int) -> Boolean
): String? {
    val text = value.trim()
    if (text.isEmpty()) return null
    if (!isSingleGrapheme(text)) return null
    var hasEmoji = false
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        hasEmoji = hasEmoji || hasEmojiProperty(codePoint)
        index += Character.charCount(codePoint)
    }
    if (!hasEmoji) return null
    if (text.length == 1 && text[0] in "0123456789#*") return null
    return text
}

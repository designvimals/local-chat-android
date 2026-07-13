package com.example.privatevault.model

import kotlin.math.roundToInt

/**
 * Stable, cross-client representation of expressive message typography.
 *
 * Only the integer value is serialized with [Message]. Unknown or invalid
 * values are clamped so older and newer clients can safely render plain text.
 */
enum class MessageEmphasis(val storedValue: Int, val progress: Float) {
    Normal(0, 0f),
    Low(1, 0.25f),
    Medium(2, 0.5f),
    High(3, 0.75f),
    Maximum(4, 1f);

    companion object {
        const val MIN_STORED_VALUE = 0
        const val MAX_STORED_VALUE = 4

        fun fromStored(value: Int): MessageEmphasis = entries[
            value.coerceIn(MIN_STORED_VALUE, MAX_STORED_VALUE)
        ]

        fun fromProgress(progress: Float): MessageEmphasis {
            val clamped = progress.coerceIn(0f, 1f)
            if (clamped < 0.125f) return Normal
            return fromStored((clamped * MAX_STORED_VALUE).roundToInt().coerceAtLeast(1))
        }

        fun sanitize(value: Int): Int = value.coerceIn(MIN_STORED_VALUE, MAX_STORED_VALUE)
    }
}

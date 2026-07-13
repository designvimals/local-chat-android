package com.example.privatevault.ui.screen.chat

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Immutable
data class MessageVisualMetrics(
    val textStyle: TextStyle,
    val horizontalPadding: Dp,
    val verticalPadding: Dp
)

fun messageVisualMetrics(
    text: String,
    progress: Float,
    baseStyle: TextStyle
): MessageVisualMetrics {
    val clamped = SendEmphasisMath.clamp(progress)
    val sizeIncrease = SendEmphasisMath.textSizeIncreaseSp(text.length) * clamped
    val baseSize = baseStyle.fontSize.value.takeIf { it > 0f } ?: 16f
    val textSize = baseSize + sizeIncrease
    val weight = (400 + 190 * clamped).roundToInt().coerceIn(400, 600)
    val paddingIncrease = SendEmphasisMath.bubblePaddingIncreaseDp(text.length) * clamped
    return MessageVisualMetrics(
        textStyle = baseStyle.copy(
            fontSize = textSize.sp,
            lineHeight = (textSize * 1.34f).sp,
            fontWeight = FontWeight(weight)
        ),
        horizontalPadding = (12f + paddingIncrease).dp,
        verticalPadding = (8.5f + paddingIncrease * 0.7f).dp
    )
}

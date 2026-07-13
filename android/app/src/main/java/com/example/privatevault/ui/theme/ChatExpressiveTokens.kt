package com.example.privatevault.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Values specific to the expressive chat experience and absent from MaterialTheme. */
object ChatExpressiveTokens {
    val ScreenHorizontalPadding = 14.dp
    val MessageVerticalSpacing = 5.dp
    val MessageMaxWidthFraction = 0.86f
    val IncomingBubbleShape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 7.dp)
    val OutgoingBubbleShape = RoundedCornerShape(22.dp, 22.dp, 7.dp, 22.dp)
    val ComposerShape = RoundedCornerShape(30.dp)
    val InputShape = RoundedCornerShape(24.dp)
    val SendButtonSize = 52.dp
    val MinimumTouchTarget = 48.dp
    val VerticalCancelDistance = 88.dp
    val MinimumDragRange = 184.dp

    const val HoldThresholdMillis = 420L
    const val HoldGrowthDurationMillis = 1_450L
    const val CancelSettleMillis = 140L
    const val ButtonPressedScale = 0.94f
    const val ButtonMaximumScale = 1.10f
    const val PopPreviewScale = 1.055f
    const val PopButtonScale = 1.08f
    const val PopSpringDamping = Spring.DampingRatioMediumBouncy
    const val PopSpringStiffness = Spring.StiffnessMedium
}

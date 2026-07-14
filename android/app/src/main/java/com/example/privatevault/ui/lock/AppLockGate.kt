package com.example.privatevault.ui.lock

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.privatevault.R
import com.example.privatevault.security.AppLockManager
import com.example.privatevault.security.AppLockState
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@Composable
fun AppLockGate(
    manager: AppLockManager,
    content: @Composable () -> Unit
) {
    val state by manager.state.collectAsState()
    when (state) {
        AppLockState.Unlocked -> content()
        AppLockState.Locked -> UnlockKeypad(onUnlock = manager::unlock)
        AppLockState.NeedsSetup -> DebugKeySetupContent(
            title = stringResource(R.string.create_debug_key),
            body = stringResource(R.string.debug_key_first_setup_body),
            confirmLabel = stringResource(R.string.save_debug_key),
            onConfirm = manager::setDebugKey
        )
    }
}

/** Full-screen keypad used by the first-run setup and the Settings reset flow. */
@Composable
fun DebugKeySetupDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: (String) -> Result<Unit>,
    onDismiss: (() -> Unit)? = null,
    onSaved: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        DebugKeySetupContent(
            title = title,
            body = body,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            onSaved = onSaved
        )
    }
}

@Composable
private fun DebugKeySetupContent(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: (String) -> Result<Unit>,
    onDismiss: (() -> Unit)? = null,
    onSaved: (() -> Unit)? = null
) {
    var digits by rememberSaveable { mutableStateOf("") }
    var firstEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val fourDigitError = stringResource(R.string.debug_key_four_digits_error)
    val mismatchError = stringResource(R.string.debug_key_mismatch_error)
    val saveError = stringResource(R.string.debug_key_save_error)
    val stageLabel = if (firstEntry == null) {
        stringResource(R.string.new_debug_key)
    } else {
        stringResource(R.string.confirm_debug_key)
    }

    fun submit() {
        if (digits.length != DEBUG_KEY_LENGTH) {
            error = fourDigitError
            return
        }
        val first = firstEntry
        if (first == null) {
            firstEntry = digits
            digits = ""
            error = null
            return
        }
        if (digits != first) {
            firstEntry = null
            digits = ""
            error = mismatchError
            return
        }
        error = onConfirm(digits).fold(
            onSuccess = { onSaved?.invoke(); null },
            onFailure = { it.message ?: saveError }
        )
    }

    PinKeypadScreen(
        title = title,
        body = body,
        stageLabel = stageLabel,
        digits = digits,
        error = error,
        submitDescription = confirmLabel,
        onDigit = { digit ->
            if (digits.length < DEBUG_KEY_LENGTH) digits += digit
            error = null
        },
        onDelete = {
            if (digits.isNotEmpty()) digits = digits.dropLast(1)
            error = null
        },
        onSubmit = ::submit,
        onDismiss = onDismiss
    )
}

@Composable
private fun UnlockKeypad(onUnlock: (String) -> Boolean) {
    var digits by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val incorrect = stringResource(R.string.debug_key_incorrect)

    fun submit() {
        if (digits.length == DEBUG_KEY_LENGTH && onUnlock(digits)) return
        error = incorrect
        digits = ""
    }

    PinKeypadScreen(
        title = stringResource(R.string.debug_key_required),
        body = stringResource(R.string.debug_key_prompt),
        stageLabel = stringResource(R.string.debug_key),
        digits = digits,
        error = error,
        submitDescription = stringResource(R.string.unlock),
        onDigit = { digit ->
            if (digits.length < DEBUG_KEY_LENGTH) digits += digit
            error = null
        },
        onDelete = {
            if (digits.isNotEmpty()) digits = digits.dropLast(1)
            error = null
        },
        onSubmit = ::submit
    )
}

@Composable
private fun PinKeypadScreen(
    title: String,
    body: String,
    stageLabel: String,
    digits: String,
    error: String?,
    submitDescription: String,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val shapePairs = remember { randomizedPinShapePairs() }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().navigationBarsPadding()) {
            val horizontalPadding = 28.dp
            val widthBasedSize = ((maxWidth - horizontalPadding * 2 - 24.dp) / 3)
            val heightBasedSize = ((maxHeight - 300.dp - 36.dp) / 4)
            val keySize = minOf(widthBasedSize, heightBasedSize, 104.dp).coerceAtLeast(64.dp)
            val compact = maxHeight < 590.dp || widthBasedSize < 64.dp
            onDismiss?.let { dismiss ->
                IconButton(
                    onClick = dismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(52.dp)
                ) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (compact) Arrangement.spacedBy(20.dp) else Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = if (compact) 20.dp else 0.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        body,
                        modifier = Modifier.widthIn(max = 390.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedContent(
                        targetState = stageLabel,
                        transitionSpec = {
                            androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()
                        },
                        label = "debug-key-stage"
                    ) { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    PinDots(
                        filledCount = digits.length,
                        shapePairs = shapePairs,
                        animationsEnabled = animationsEnabled
                    )
                    Box(
                        Modifier.height(42.dp).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }
                }
                NumericKeypad(
                    onDigit = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit(it)
                    },
                    onDelete = onDelete,
                    onSubmit = onSubmit,
                    submitDescription = submitDescription,
                    submitEnabled = digits.length == DEBUG_KEY_LENGTH,
                    keySize = keySize
                )
                if (compact) Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinDots(
    filledCount: Int,
    shapePairs: List<PinShapePair>,
    animationsEnabled: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(DEBUG_KEY_LENGTH) { index ->
            val filled = index < filledCount
            val progress by animateFloatAsState(
                targetValue = if (filled) 1f else 0f,
                animationSpec = if (animationsEnabled) tween(180) else snap(),
                label = "debug-key-shape-$index"
            )
            val indicatorSize by animateDpAsState(
                targetValue = if (filled) 22.dp else 18.dp,
                animationSpec = if (animationsEnabled) tween(180) else snap(),
                label = "debug-key-size-$index"
            )
            Surface(
                modifier = Modifier.size(indicatorSize),
                shape = MorphProgressShape(shapePairs[index].morph, progress),
                color = if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ) {}
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    submitDescription: String,
    submitEnabled: Boolean,
    keySize: androidx.compose.ui.unit.Dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> PinNumberButton(digit, keySize) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onDelete, modifier = Modifier.size(keySize)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.back), Modifier.size(23.dp))
            }
            PinNumberButton("0", keySize) { onDigit("0") }
            FilledIconButton(
                onClick = onSubmit,
                enabled = submitEnabled,
                modifier = Modifier.size(keySize).semantics { contentDescription = submitDescription }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun PinNumberButton(digit: String, keySize: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(keySize),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(digit, fontSize = 30.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun randomizedPinShapePairs(): List<PinShapePair> {
    val starts = listOf(
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Diamond,
        MaterialShapes.Flower,
        MaterialShapes.Gem,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Pentagon,
        MaterialShapes.PuffyDiamond,
        MaterialShapes.Sunny
    ).shuffled().take(DEBUG_KEY_LENGTH)
    val ends = starts.drop(1) + starts.first()
    return starts.zip(ends) { start, end -> PinShapePair(Morph(start, end)) }
}

private data class PinShapePair(val morph: Morph)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphProgressShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        morph.toPath(progress, path)
        val matrix = Matrix().apply { scale(size.width, size.height) }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

private const val DEBUG_KEY_LENGTH = 4

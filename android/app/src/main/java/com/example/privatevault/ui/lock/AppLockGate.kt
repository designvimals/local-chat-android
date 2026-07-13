package com.example.privatevault.ui.lock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.privatevault.R
import com.example.privatevault.security.AppLockManager
import com.example.privatevault.security.AppLockState

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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().navigationBarsPadding()) {
            onDismiss?.let { dismiss ->
                IconButton(
                    onClick = dismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(52.dp)
                ) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.30f))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    body,
                    modifier = Modifier.widthIn(max = 390.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
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
                Spacer(Modifier.height(18.dp))
                PinDots(filledCount = digits.length)
                Box(
                    Modifier.height(52.dp).fillMaxWidth(),
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
                Spacer(Modifier.height(6.dp))
                NumericKeypad(
                    onDigit = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit(it)
                    },
                    onDelete = onDelete,
                    onSubmit = onSubmit,
                    submitDescription = submitDescription,
                    submitEnabled = digits.length == DEBUG_KEY_LENGTH
                )
                Spacer(Modifier.weight(0.24f))
            }
        }
    }
}

@Composable
private fun PinDots(filledCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(DEBUG_KEY_LENGTH) { index ->
            Surface(
                modifier = Modifier.size(if (index < filledCount) 14.dp else 12.dp),
                shape = CircleShape,
                color = if (index < filledCount) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = if (index < filledCount) null else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
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
    submitEnabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { digit -> PinNumberButton(digit) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            PinIconButton(onClick = onDelete) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.back), Modifier.size(23.dp))
            }
            PinNumberButton("0") { onDigit("0") }
            PinIconButton(
                onClick = onSubmit,
                enabled = submitEnabled,
                emphasized = true,
                description = submitDescription
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(25.dp))
            }
        }
    }
}

@Composable
private fun PinNumberButton(digit: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(68.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PinIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    description: String? = null,
    content: @Composable () -> Unit
) {
    val descriptionModifier = if (description != null) {
        Modifier.semantics { contentDescription = description }
    } else Modifier
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(68.dp).then(descriptionModifier),
        shape = CircleShape,
        color = if (emphasized) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

private const val DEBUG_KEY_LENGTH = 4

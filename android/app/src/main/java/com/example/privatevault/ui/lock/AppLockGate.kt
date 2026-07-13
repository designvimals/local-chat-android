package com.example.privatevault.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
        AppLockState.Locked -> LockBackground {
            UnlockDialog(onUnlock = manager::unlock)
        }
        AppLockState.NeedsSetup -> LockBackground {
            DebugKeySetupDialog(
                title = stringResource(R.string.create_debug_key),
                body = stringResource(R.string.debug_key_first_setup_body),
                confirmLabel = stringResource(R.string.save_debug_key),
                onConfirm = manager::setDebugKey
            )
        }
    }
}

@Composable
fun DebugKeySetupDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: (String) -> Result<Unit>,
    onDismiss: (() -> Unit)? = null,
    onSaved: (() -> Unit)? = null
) {
    var debugKey by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val fourDigitError = stringResource(R.string.debug_key_four_digits_error)
    val mismatchError = stringResource(R.string.debug_key_mismatch_error)
    val saveError = stringResource(R.string.debug_key_save_error)
    val focusManager = LocalFocusManager.current

    fun submit() {
        error = when {
            debugKey.length != DEBUG_KEY_LENGTH -> fourDigitError
            confirmation.length != DEBUG_KEY_LENGTH -> fourDigitError
            debugKey != confirmation -> mismatchError
            else -> onConfirm(debugKey).fold(
                onSuccess = {
                    focusManager.clearFocus()
                    onSaved?.invoke()
                    null
                },
                onFailure = { it.message ?: saveError }
            )
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = onDismiss != null
        ),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DebugKeyField(
                    value = debugKey,
                    onValueChange = {
                        debugKey = it
                        error = null
                    },
                    label = stringResource(R.string.new_debug_key),
                    imeAction = ImeAction.Next
                )
                DebugKeyField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        error = null
                    },
                    label = stringResource(R.string.confirm_debug_key),
                    imeAction = ImeAction.Done,
                    onDone = ::submit
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit) {
                Text(confirmLabel)
            }
        },
        dismissButton = onDismiss?.let { dismiss ->
            {
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun UnlockDialog(onUnlock: (String) -> Boolean) {
    var debugKey by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (debugKey.length == DEBUG_KEY_LENGTH && onUnlock(debugKey)) {
            focusManager.clearFocus()
        } else {
            error = true
            debugKey = ""
        }
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.debug_key_required)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.debug_key_prompt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DebugKeyField(
                    value = debugKey,
                    onValueChange = {
                        debugKey = it
                        error = false
                    },
                    label = stringResource(R.string.debug_key),
                    imeAction = ImeAction.Done,
                    onDone = ::submit
                )
                if (error) {
                    Text(
                        stringResource(R.string.debug_key_incorrect),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit) {
                Text(stringResource(R.string.unlock))
            }
        }
    )
}

@Composable
private fun DebugKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            onValueChange(candidate.filter(Char::isDigit).take(DEBUG_KEY_LENGTH))
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
    )
}

@Composable
private fun LockBackground(dialog: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {}
    }
    dialog()
}

private const val DEBUG_KEY_LENGTH = 4

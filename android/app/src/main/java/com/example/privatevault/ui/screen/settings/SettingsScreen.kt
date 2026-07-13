package com.example.privatevault.ui.screen.settings

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.privatevault.R
import com.example.privatevault.data.local.ThemePreference
import com.example.privatevault.ui.lock.DebugKeySetupDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    onBackupNow: suspend () -> Result<String>,
    onResetDebugKey: (String) -> Result<Unit>,
    themePreference: ThemePreference,
    onThemePreferenceChanged: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var backingUp by remember { mutableStateOf(false) }
    var debugKeyTapCount by remember { mutableIntStateOf(0) }
    var debugKeyTapWindowStartedAt by remember { mutableLongStateOf(0L) }
    var showDebugKeyReset by remember { mutableStateOf(false) }
    var debugKeyStatus by remember { mutableStateOf<String?>(null) }
    var showFontLicense by remember { mutableStateOf(false) }
    val debugKeyAccessibility = stringResource(R.string.debug_key_accessibility)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.appearance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { preference ->
                            val label = when (preference) {
                                ThemePreference.System -> stringResource(R.string.theme_system)
                                ThemePreference.Light -> stringResource(R.string.theme_light)
                                ThemePreference.Dark -> stringResource(R.string.theme_dark)
                            }
                            FilterChip(
                                selected = themePreference == preference,
                                onClick = { onThemePreferenceChanged(preference) },
                                label = { Text(label) }
                            )
                        }
                    }
                    TextButton(onClick = { showFontLicense = true }) {
                        Text(stringResource(R.string.open_source_licenses))
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.paired_device),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.contact_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onOpenPairing) {
                        Text(stringResource(R.string.pairing))
                    }
                }
            }
            Card(
                onClick = {
                    val now = SystemClock.elapsedRealtime()
                    if (now - debugKeyTapWindowStartedAt > DEBUG_KEY_TAP_WINDOW_MILLIS) {
                        debugKeyTapWindowStartedAt = now
                        debugKeyTapCount = 1
                    } else {
                        debugKeyTapCount += 1
                    }
                    if (debugKeyTapCount >= DEBUG_KEY_RESET_TAPS) {
                        debugKeyTapCount = 0
                        debugKeyTapWindowStartedAt = 0L
                        debugKeyStatus = null
                        showDebugKeyReset = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = debugKeyAccessibility
                    }
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.debug_key),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.debug_key_mask),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    debugKeyStatus?.let { status ->
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.chat_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.chat_backup_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.automatic_backup_time),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        enabled = !backingUp,
                        onClick = {
                            scope.launch {
                                backingUp = true
                                backupStatus = onBackupNow().fold(
                                    onSuccess = { path -> resources.getString(R.string.backup_created, path) },
                                    onFailure = { error -> error.message ?: resources.getString(R.string.backup_failed) }
                                )
                                backingUp = false
                            }
                        }
                    ) {
                        Text(if (backingUp) stringResource(R.string.backing_up) else stringResource(R.string.backup_now))
                    }
                    backupStatus?.let { status ->
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showDebugKeyReset) {
        val updatedMessage = stringResource(R.string.debug_key_updated)
        DebugKeySetupDialog(
            title = stringResource(R.string.reset_debug_key),
            body = stringResource(R.string.reset_debug_key_body),
            confirmLabel = stringResource(R.string.reset_debug_key),
            onConfirm = onResetDebugKey,
            onDismiss = { showDebugKeyReset = false },
            onSaved = {
                showDebugKeyReset = false
                debugKeyStatus = updatedMessage
            }
        )
    }

    if (showFontLicense) {
        val licenseText = remember(resources) {
            resources.openRawResource(R.raw.google_sans_flex_ofl)
                .bufferedReader()
                .use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showFontLicense = false },
            title = { Text(stringResource(R.string.google_sans_flex_license_title)) },
            text = {
                Text(
                    text = licenseText,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showFontLicense = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

private const val DEBUG_KEY_RESET_TAPS = 7
private const val DEBUG_KEY_TAP_WINDOW_MILLIS = 3_000L

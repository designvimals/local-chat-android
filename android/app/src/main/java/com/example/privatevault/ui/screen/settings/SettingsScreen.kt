package com.example.privatevault.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit,
    onSharingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val sharingEnabled by viewModel.storageSharingEnabled.collectAsState(initial = false)
    val permissionGranted by viewModel.storagePermissionGranted.collectAsState(initial = false)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Storage Sharing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StorageSharingCard(
                sharingEnabled = sharingEnabled,
                permissionGranted = permissionGranted,
                activeBrowsing = false,
                onPause = { viewModel.setSharingEnabled(false, onSharingChanged) },
                onResume = { viewModel.setSharingEnabled(true, onSharingChanged) }
            )
            Text("Paired device: Vimal", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onOpenPairing) {
                Text("Pairing")
            }
        }
    }
}

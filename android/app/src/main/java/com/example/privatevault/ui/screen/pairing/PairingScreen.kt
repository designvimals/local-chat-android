package com.example.privatevault.ui.screen.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.privatevault.network.BackendRegistrationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onBack: () -> Unit,
    onCodeRotated: () -> Unit,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val token by viewModel.pairingCode.collectAsState()
    val pairingAvailable by viewModel.pairingAvailable.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Pairing") },
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
            Card {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Web pairing code", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (pairingAvailable) {
                            "Enter this code on the web portal to connect chat and storage to this phone."
                        } else {
                            "This phone is already paired. Create a new code only to replace the paired browser."
                        }
                    )
                    if (pairingAvailable && registrationState is BackendRegistrationState.Registered) {
                        SelectionContainer {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (pairingAvailable && registrationState is BackendRegistrationState.Failed) {
                        Text(
                            registrationState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onRetryRegistration) { Text("Retry relay connection") }
                    }
                    if (pairingAvailable && registrationState !is BackendRegistrationState.Registered &&
                        registrationState !is BackendRegistrationState.Failed
                    ) {
                        Text("Registering this code with the relay…")
                    }
                    Text(
                        "A code works once. Rotate it only when pairing a replacement browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { viewModel.rotate(onCodeRotated) }) {
                        Text("Create new pairing code")
                    }
                }
            }
        }
    }
}

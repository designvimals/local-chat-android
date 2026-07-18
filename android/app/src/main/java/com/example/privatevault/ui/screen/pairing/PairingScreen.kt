package com.example.privatevault.ui.screen.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.privatevault.network.BackendRegistrationState
import com.example.privatevault.network.PeerConnectionState
import com.example.privatevault.R

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
    val peerState by viewModel.peerState.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val claimingPeer by viewModel.claimingPeer.collectAsState()
    val peerError by viewModel.peerError.collectAsState()
    var phoneCode by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pairing)) },
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
            Card {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.web_pairing_code), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (pairingAvailable) {
                            stringResource(R.string.pairing_available_body)
                        } else {
                            stringResource(R.string.pairing_claimed_body)
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
                            stringResource(R.string.connection_service_unavailable),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onRetryRegistration) { Text(stringResource(R.string.retry_relay_connection)) }
                    }
                    if (pairingAvailable && registrationState !is BackendRegistrationState.Registered &&
                        registrationState !is BackendRegistrationState.Failed
                    ) {
                        Text(stringResource(R.string.pairing_registering))
                    }
                    Text(
                        stringResource(R.string.pairing_once_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { viewModel.rotate(onCodeRotated) }) {
                        Text(stringResource(R.string.create_new_pairing_code))
                    }
                }
            }
            Card {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.phone_pairing_title), style = MaterialTheme.typography.titleLarge)
                    if (peerName != null) {
                        Text(stringResource(R.string.phone_pairing_connected, peerName ?: ""))
                        Text(
                            when (peerState) {
                                is PeerConnectionState.Connected -> stringResource(R.string.status_connected)
                                is PeerConnectionState.Failed -> stringResource(R.string.status_relay_offline)
                                else -> stringResource(R.string.status_connecting_relay)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = viewModel::disconnectPhone) {
                            Text(stringResource(R.string.disconnect_phone))
                        }
                    } else {
                        Text(stringResource(R.string.phone_pairing_body))
                        OutlinedTextField(
                            value = phoneCode,
                            onValueChange = { value -> phoneCode = value.filter(Char::isDigit).take(6) },
                            label = { Text(stringResource(R.string.six_digit_code)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = peerError != null
                        )
                        peerError?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { viewModel.connectPhone(phoneCode) },
                            enabled = phoneCode.length == 6 && !claimingPeer
                        ) {
                            Text(if (claimingPeer) stringResource(R.string.connecting_phone) else stringResource(R.string.connect_phone))
                        }
                    }
                }
            }
        }
    }
}

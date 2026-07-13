package com.example.privatevault.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.privatevault.network.BackendRegistrationState
import com.example.privatevault.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    pairingCode: String,
    pairingAvailable: Boolean,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit,
    onOpenSettings: () -> Unit,
    onAttachFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val viewerConnected by viewModel.viewerConnected.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages, viewerConnected, pairingAvailable) {
        viewModel.markIncomingRead()
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex + if (!viewerConnected && pairingAvailable) 1 else 0)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 5.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.contact_initial).take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text(stringResource(R.string.contact_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (viewerConnected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        viewerConnected -> stringResource(R.string.status_connected)
                                        registrationState is BackendRegistrationState.Registered -> stringResource(R.string.status_code_active)
                                        registrationState is BackendRegistrationState.Failed -> stringResource(R.string.status_relay_offline)
                                        else -> stringResource(R.string.status_connecting_relay)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(onSend = viewModel::send, onAttachFile = onAttachFile)
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (!viewerConnected && pairingAvailable) {
                item(key = "pairing-card") {
                    PairingCard(
                        pairingCode = pairingCode,
                        registrationState = registrationState,
                        onRetryRegistration = onRetryRegistration
                    )
                }
            }

            if (messages.isEmpty()) {
                item(key = "empty-chat") {
                    EmptyConversation(viewerConnected = viewerConnected, pairingAvailable = pairingAvailable)
                }
            } else {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = viewModel.isMine(message)
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingCard(
    pairingCode: String,
    registrationState: BackendRegistrationState,
    onRetryRegistration: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(9.dp))
                Text(stringResource(R.string.pairing_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            when (registrationState) {
                is BackendRegistrationState.Registered -> {
                    Text(
                        stringResource(R.string.pairing_card_registered_body),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SelectionContainer {
                        Text(
                            text = pairingCode.chunked(3).joinToString("  "),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is BackendRegistrationState.Failed -> {
                    Text(
                        registrationState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onRetryRegistration) { Text(stringResource(R.string.retry_relay_connection)) }
                }
                BackendRegistrationState.Connecting,
                BackendRegistrationState.Idle -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.pairing_registering))
                    }
                }
            }
            Text(
                stringResource(R.string.messages_device_only),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun EmptyConversation(viewerConnected: Boolean, pairingAvailable: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("↗", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (viewerConnected) stringResource(R.string.empty_connected_title) else if (pairingAvailable) stringResource(R.string.empty_pairing_title) else stringResource(R.string.empty_paired_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (viewerConnected) {
                stringResource(R.string.empty_connected_body)
            } else if (pairingAvailable) {
                stringResource(R.string.empty_pairing_body)
            } else {
                stringResource(R.string.empty_paired_body)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

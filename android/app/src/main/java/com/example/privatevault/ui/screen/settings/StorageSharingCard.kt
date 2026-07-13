package com.example.privatevault.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StorageSharingCard(
    sharingEnabled: Boolean,
    permissionGranted: Boolean,
    activeBrowsing: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when {
        activeBrowsing -> "Vimal is browsing your storage"
        sharingEnabled -> "Storage sharing is on"
        else -> "Storage sharing is paused"
    }
    val body = when {
        activeBrowsing -> "Keep the app open for the smoothest file transfer."
        sharingEnabled -> "Vimal can browse shared storage from this phone when it is online."
        else -> "Vimal can still chat with you, but cannot browse files until you turn sharing back on."
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (permissionGranted) "Storage permission allowed" else "Storage permission missing",
                style = MaterialTheme.typography.labelLarge,
                color = if (permissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            if (sharingEnabled) {
                OutlinedButton(onClick = onPause) {
                    Text("Pause Sharing")
                }
            } else {
                Button(onClick = onResume, enabled = permissionGranted) {
                    Text("Resume Sharing")
                }
            }
        }
    }
}

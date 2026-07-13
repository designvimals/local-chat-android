package com.example.privatevault.ui.screen.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.privatevault.R

@Composable
fun MessageInputBar(
    onSend: (String) -> Unit,
    onAttachFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val trimmed = text.trim()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(onClick = onAttachFile) {
            Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.attach_file))
        }
        OutlinedTextField(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.message_placeholder)) },
            maxLines = 4,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
        )
        IconButton(
            enabled = trimmed.isNotBlank(),
            onClick = {
                onSend(trimmed)
                text = ""
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send_message))
        }
    }
}

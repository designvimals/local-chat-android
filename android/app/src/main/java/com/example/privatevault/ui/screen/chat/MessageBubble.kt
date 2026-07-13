package com.example.privatevault.ui.screen.chat

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.privatevault.model.Message
import com.example.privatevault.util.TimeUtils

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bubbleMaxWidth = maxWidth * 0.78f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier.widthIn(max = bubbleMaxWidth),
                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = if (isMine) {
                    RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)
                } else {
                    RoundedCornerShape(22.dp, 22.dp, 22.dp, 8.dp)
                },
                tonalElevation = if (isMine) 2.dp else 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = TimeUtils.display(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isMine) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = receiptText(message.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.status == "read") Color(0xFF9EDCFF) else Color.Unspecified
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun receiptText(status: String): String = when (status) {
    "read" -> "✓✓ Read"
    "delivered" -> "✓✓"
    "sent" -> "✓"
    "failed" -> "Waiting"
    else -> "Waiting"
}

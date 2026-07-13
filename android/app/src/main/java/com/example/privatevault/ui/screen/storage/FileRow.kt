package com.example.privatevault.ui.screen.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.privatevault.R
import com.example.privatevault.model.FileItem
import com.example.privatevault.util.FileUtils

@Composable
fun FileRow(
    item: FileItem,
    onOpenFolder: (FileItem) -> Unit,
    onDownload: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val isFolder = item.type == "folder"
    ListItem(
        modifier = modifier.clickable {
            if (isFolder) onOpenFolder(item) else onDownload(item)
        },
        leadingContent = {
            Icon(
                painter = painterResource(if (isFolder) R.drawable.ic_folder_24 else R.drawable.ic_file_24),
                contentDescription = null,
                tint = if (isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        },
        headlineContent = {
            Text(text = item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row {
                Text(if (isFolder) "Folder" else FileUtils.displaySize(item.size))
                item.lastModified?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(it.take(10))
                }
            }
        },
        trailingContent = {
            if (!isFolder) {
                IconButton(onClick = { onDownload(item) }) {
                    Icon(painterResource(R.drawable.ic_download_24), contentDescription = "Download ${item.name}")
                }
            }
        }
    )
}

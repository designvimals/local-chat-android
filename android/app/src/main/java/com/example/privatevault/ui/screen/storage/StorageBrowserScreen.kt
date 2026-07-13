package com.example.privatevault.ui.screen.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.privatevault.util.PathUtils
import com.example.privatevault.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageBrowserScreen(
    viewModel: StorageBrowserViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val downloadWebOnlyMessage = stringResource(R.string.storage_download_web_only)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.load("/")
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.storage_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BreadcrumbBar(
                path = state.path,
                onBack = {
                    if (state.path == "/") onClose() else viewModel.load(PathUtils.parent(state.path))
                }
            )

            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                state.error != null -> Text(
                    text = stringResource(R.string.storage_unavailable),
                    modifier = Modifier.padding(24.dp)
                )
                state.items.isEmpty() -> Text(stringResource(R.string.storage_empty), modifier = Modifier.padding(24.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.items, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            onOpenFolder = { viewModel.load(it.path) },
                            onDownload = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(downloadWebOnlyMessage)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

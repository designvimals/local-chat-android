package com.example.privatevault.ui.screen.storage

import androidx.lifecycle.ViewModel
import com.example.privatevault.data.repository.StorageRepository
import com.example.privatevault.model.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class StorageBrowserViewModel(private val storageRepository: StorageRepository) : ViewModel() {
    private val _state = MutableStateFlow(StorageBrowserUiState())
    val state: StateFlow<StorageBrowserUiState> = _state

    fun load(path: String) {
        _state.update { it.copy(path = path, loading = true, error = null) }
        val result = runCatching { storageRepository.list(path) }
        _state.update {
            result.fold(
                onSuccess = { items -> it.copy(path = path, items = items, loading = false, error = null) },
                onFailure = { failure -> it.copy(loading = false, error = failure.message ?: "Storage is not available.") }
            )
        }
    }
}

data class StorageBrowserUiState(
    val path: String = "/",
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

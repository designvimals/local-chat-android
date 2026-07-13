package com.example.privatevault.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.data.local.SettingsStore
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsStore: SettingsStore) : ViewModel() {
    val storageSharingEnabled = settingsStore.storageSharingEnabled
    val storagePermissionGranted = settingsStore.storagePermissionGranted

    fun setSharingEnabled(enabled: Boolean, onChanged: (Boolean) -> Unit) {
        viewModelScope.launch {
            settingsStore.setStorageSharingEnabled(enabled)
            onChanged(enabled)
        }
    }
}

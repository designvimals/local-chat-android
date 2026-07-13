package com.example.privatevault.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.data.local.SettingsStore
import kotlinx.coroutines.launch

class OnboardingViewModel(private val settingsStore: SettingsStore) : ViewModel() {
    fun complete(permissionGranted: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsStore.completeOnboarding(permissionGranted)
            onComplete()
        }
    }
}

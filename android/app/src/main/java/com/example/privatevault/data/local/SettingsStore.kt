package com.example.privatevault.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemePreference {
    System,
    Light,
    Dark;

    companion object {
        fun fromStored(value: String?): ThemePreference =
            entries.firstOrNull { it.name == value } ?: System
    }
}

enum class ChatBubblePalette {
    Lavender,
    Ocean,
    Jade,
    Coral,
    Rose,
    Amber;

    companion object {
        fun fromStored(value: String?): ChatBubblePalette =
            entries.firstOrNull { it.name == value } ?: Lavender
    }
}

class SettingsStore(private val context: Context) {
    val onboardingComplete: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.OnboardingComplete] ?: false
    }

    val storageSharingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.StorageSharingEnabled] ?: false
    }

    val storagePermissionGranted: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.StoragePermissionGranted] ?: false
    }

    val manuallyPaused: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.ManuallyPaused] ?: false
    }

    val themePreference: Flow<ThemePreference> = context.settingsDataStore.data.map { preferences ->
        ThemePreference.fromStored(preferences[Keys.ThemePreference])
    }

    val chatBubblePalette: Flow<ChatBubblePalette> = context.settingsDataStore.data.map { preferences ->
        ChatBubblePalette.fromStored(preferences[Keys.ChatBubblePalette])
    }

    suspend fun completeOnboarding(storagePermissionGranted: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.OnboardingComplete] = true
            preferences[Keys.StoragePermissionGranted] = storagePermissionGranted
            if (storagePermissionGranted && preferences[Keys.ManuallyPaused] != true) {
                preferences[Keys.StorageSharingEnabled] = true
            }
        }
    }

    suspend fun setStoragePermissionGranted(granted: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.StoragePermissionGranted] = granted
            if (granted && preferences[Keys.ManuallyPaused] != true) {
                preferences[Keys.StorageSharingEnabled] = true
            }
        }
    }

    suspend fun setStorageSharingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.StorageSharingEnabled] = enabled
            preferences[Keys.ManuallyPaused] = !enabled
        }
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ThemePreference] = preference.name
        }
    }

    suspend fun setChatBubblePalette(palette: ChatBubblePalette) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ChatBubblePalette] = palette.name
        }
    }

    private object Keys {
        val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
        val StorageSharingEnabled = booleanPreferencesKey("storage_sharing_enabled")
        val StoragePermissionGranted = booleanPreferencesKey("storage_permission_granted")
        val ManuallyPaused = booleanPreferencesKey("manually_paused")
        val ThemePreference = stringPreferencesKey("theme_preference")
        val ChatBubblePalette = stringPreferencesKey("chat_bubble_palette")
    }
}

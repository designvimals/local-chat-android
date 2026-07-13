package com.example.privatevault.data.local

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID

class TokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("tokens", Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing

        val created = "phone_${UUID.randomUUID()}"
        preferences.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun getAccessToken(): String {
        val existing = preferences.getString(KEY_ACCESS_TOKEN, null)
        if (!existing.isNullOrBlank() && existing.length >= 32) return existing

        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        preferences.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        return token
    }

    fun getPairingCode(): String {
        val existing = preferences.getString(KEY_PAIRING_CODE, null)
        if (!existing.isNullOrBlank()) return existing

        val code = createPairingCode()
        preferences.edit().putString(KEY_PAIRING_CODE, code).apply()
        return code
    }

    fun rotatePairingCode(): String {
        val code = createPairingCode()
        preferences.edit()
            .putString(KEY_PAIRING_CODE, code)
            .putBoolean(KEY_PAIRING_CLAIMED, false)
            .apply()
        return code
    }

    fun isPairingClaimed(): Boolean = preferences.getBoolean(KEY_PAIRING_CLAIMED, false)

    fun markPairingClaimed() {
        preferences.edit().putBoolean(KEY_PAIRING_CLAIMED, true).apply()
    }

    private fun createPairingCode(): String {
        return (100000 + random.nextInt(900000)).toString()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_PAIRING_CODE = "pairing_code"
        const val KEY_PAIRING_CLAIMED = "pairing_claimed"
        val random = SecureRandom()
    }
}

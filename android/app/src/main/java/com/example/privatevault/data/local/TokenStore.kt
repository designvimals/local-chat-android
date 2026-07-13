package com.example.privatevault.data.local

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import java.util.UUID

data class PeerConnection(
    val accessToken: String,
    val viewerDeviceId: String,
    val friendName: String
)

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

    fun getPeerConnection(): PeerConnection? {
        val accessToken = preferences.getString(KEY_PEER_ACCESS_TOKEN, null)?.takeIf { it.length >= 32 } ?: return null
        val viewerDeviceId = preferences.getString(KEY_PEER_VIEWER_DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val friendName = preferences.getString(KEY_PEER_FRIEND_NAME, null)?.takeIf { it.isNotBlank() } ?: "Paired phone"
        return PeerConnection(accessToken, viewerDeviceId, friendName)
    }

    fun savePeerConnection(connection: PeerConnection) {
        preferences.edit()
            .putString(KEY_PEER_ACCESS_TOKEN, connection.accessToken)
            .putString(KEY_PEER_VIEWER_DEVICE_ID, connection.viewerDeviceId)
            .putString(KEY_PEER_FRIEND_NAME, connection.friendName)
            .apply()
    }

    fun clearPeerConnection() {
        preferences.edit()
            .remove(KEY_PEER_ACCESS_TOKEN)
            .remove(KEY_PEER_VIEWER_DEVICE_ID)
            .remove(KEY_PEER_FRIEND_NAME)
            .apply()
    }

    private fun createPairingCode(): String {
        return (100000 + random.nextInt(900000)).toString()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_PAIRING_CODE = "pairing_code"
        const val KEY_PAIRING_CLAIMED = "pairing_claimed"
        const val KEY_PEER_ACCESS_TOKEN = "peer_access_token"
        const val KEY_PEER_VIEWER_DEVICE_ID = "peer_viewer_device_id"
        const val KEY_PEER_FRIEND_NAME = "peer_friend_name"
        val random = SecureRandom()
    }
}

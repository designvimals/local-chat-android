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
            .putBoolean(KEY_PAIRING_CLAIMED_WEB, false)
            .putBoolean(KEY_PAIRING_CLAIMED_ANDROID, false)
            .apply()
        return code
    }

    fun hasOpenPairingSlot(): Boolean = pairingSlotState().hasOpenSlot

    fun hasClaimedPairingSlot(): Boolean = pairingSlotState().claimedClientTypes().isNotEmpty()

    fun claimedPairingClientTypes(): List<String> = pairingSlotState().claimedClientTypes()

    fun markPairingClaimed(clientType: PairingClientType?) {
        val updated = clientType?.let(pairingSlotState()::claim)
            ?: PairingSlotState(webClaimed = true, androidClaimed = true)
        preferences.edit()
            .putBoolean(KEY_PAIRING_CLAIMED_WEB, updated.webClaimed)
            .putBoolean(KEY_PAIRING_CLAIMED_ANDROID, updated.androidClaimed)
            .putBoolean(KEY_PAIRING_CLAIMED, updated.isFullyClaimed)
            .apply()
    }

    fun getPeerConnection(): PeerConnection? {
        val accessToken = preferences.getString(KEY_PEER_ACCESS_TOKEN, null)?.takeIf { it.length >= 32 } ?: return null
        val viewerDeviceId = preferences.getString(KEY_PEER_VIEWER_DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val friendName = preferences.getString(KEY_PEER_FRIEND_NAME, null)?.takeIf { it.isNotBlank() } ?: "Paired phone"
        return PeerConnection(accessToken, viewerDeviceId, friendName)
    }

    fun savePeerConnection(connection: PeerConnection) {
        val previousViewerDeviceId = preferences.getString(KEY_PEER_VIEWER_DEVICE_ID, null)
        preferences.edit()
            .putString(KEY_PEER_ACCESS_TOKEN, connection.accessToken)
            .putString(KEY_PEER_VIEWER_DEVICE_ID, connection.viewerDeviceId)
            .putString(KEY_PEER_FRIEND_NAME, connection.friendName)
            .also { editor ->
                if (previousViewerDeviceId != null && previousViewerDeviceId != connection.viewerDeviceId) {
                    editor.remove(KEY_PEER_LAST_SEEN_AT)
                }
            }
            .apply()
    }

    fun getPeerLastSeenAtMillis(): Long? = preferences
        .getLong(KEY_PEER_LAST_SEEN_AT, 0L)
        .takeIf { it > 0L }

    fun setPeerLastSeenAtMillis(timestampMillis: Long) {
        require(timestampMillis > 0L) { "Last-seen timestamps must be positive." }
        preferences.edit().putLong(KEY_PEER_LAST_SEEN_AT, timestampMillis).apply()
    }

    fun clearPeerConnection() {
        preferences.edit()
            .remove(KEY_PEER_ACCESS_TOKEN)
            .remove(KEY_PEER_VIEWER_DEVICE_ID)
            .remove(KEY_PEER_FRIEND_NAME)
            .remove(KEY_PEER_LAST_SEEN_AT)
            .apply()
    }

    private fun createPairingCode(): String {
        return (100000 + random.nextInt(900000)).toString()
    }

    private fun pairingSlotState(): PairingSlotState {
        val hasTypedState = preferences.contains(KEY_PAIRING_CLAIMED_WEB) ||
            preferences.contains(KEY_PAIRING_CLAIMED_ANDROID)
        if (!hasTypedState) {
            return PairingSlotState.fromLegacyClaimed(
                preferences.getBoolean(KEY_PAIRING_CLAIMED, false)
            )
        }
        return PairingSlotState(
            webClaimed = preferences.getBoolean(KEY_PAIRING_CLAIMED_WEB, false),
            androidClaimed = preferences.getBoolean(KEY_PAIRING_CLAIMED_ANDROID, false)
        )
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_PAIRING_CODE = "pairing_code"
        const val KEY_PAIRING_CLAIMED = "pairing_claimed"
        const val KEY_PAIRING_CLAIMED_WEB = "pairing_claimed_web"
        const val KEY_PAIRING_CLAIMED_ANDROID = "pairing_claimed_android"
        const val KEY_PEER_ACCESS_TOKEN = "peer_access_token"
        const val KEY_PEER_VIEWER_DEVICE_ID = "peer_viewer_device_id"
        const val KEY_PEER_FRIEND_NAME = "peer_friend_name"
        const val KEY_PEER_LAST_SEEN_AT = "peer_last_seen_at"
        val random = SecureRandom()
    }
}

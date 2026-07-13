package com.example.privatevault.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLockState {
    NeedsSetup,
    Locked,
    Unlocked
}

/**
 * Keeps the four-digit debug key out of app storage. Only an HMAC digest is
 * persisted; its non-exportable signing key lives in Android Keystore.
 */
class AppLockManager(context: Context) {
    private val contentResolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    private val initialState = if (hasValidConfiguration()) AppLockState.Locked else AppLockState.NeedsSetup
    private val mutableState = MutableStateFlow(initialState)
    private var backgroundStartedAt: Long? = null
    private var lastUserInteractionAt: Long = SystemClock.elapsedRealtime()

    val state: StateFlow<AppLockState> = mutableState.asStateFlow()

    fun onAppForegrounded() {
        val backgroundAt = backgroundStartedAt
        backgroundStartedAt = null
        if (
            mutableState.value == AppLockState.Unlocked &&
            backgroundAt != null &&
            SystemClock.elapsedRealtime() - backgroundAt >= LOCK_TIMEOUT_MILLIS
        ) {
            mutableState.value = AppLockState.Locked
        }
    }

    fun onAppBackgrounded() {
        if (mutableState.value == AppLockState.Unlocked) {
            backgroundStartedAt = SystemClock.elapsedRealtime()
        }
    }

    fun onUserInteraction() {
        lastUserInteractionAt = SystemClock.elapsedRealtime()
    }

    fun lockForDeviceScreenOff() {
        val screenTimeout = runCatching {
            Settings.System.getLong(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                DEFAULT_SCREEN_TIMEOUT_MILLIS
            )
        }.getOrDefault(DEFAULT_SCREEN_TIMEOUT_MILLIS).coerceAtLeast(MIN_SCREEN_TIMEOUT_MILLIS)
        val idleFor = SystemClock.elapsedRealtime() - lastUserInteractionAt
        val automaticTimeoutWindow = (screenTimeout / 10L).coerceIn(2_000L, 10_000L)
        val screenLikelyTurnedOffManually = idleFor < screenTimeout - automaticTimeoutWindow
        if (!screenLikelyTurnedOffManually) return
        backgroundStartedAt = null
        if (preferences.contains(KEY_DEBUG_KEY_DIGEST)) {
            mutableState.value = AppLockState.Locked
        }
    }

    fun unlock(debugKey: String): Boolean {
        if (!DEBUG_KEY_PATTERN.matches(debugKey)) return false
        val savedDigest = preferences.getString(KEY_DEBUG_KEY_DIGEST, null) ?: return false
        val suppliedDigest = runCatching { digest(debugKey) }.getOrNull() ?: return false
        val expectedDigest = runCatching { Base64.decode(savedDigest, Base64.NO_WRAP) }.getOrNull()
            ?: return false
        val matches = MessageDigest.isEqual(
            expectedDigest,
            suppliedDigest
        )
        if (matches) {
            backgroundStartedAt = null
            mutableState.value = AppLockState.Unlocked
        }
        return matches
    }

    fun setDebugKey(debugKey: String): Result<Unit> = runCatching {
        require(DEBUG_KEY_PATTERN.matches(debugKey)) { "Debug key must contain exactly four digits." }
        val encodedDigest = Base64.encodeToString(digest(debugKey), Base64.NO_WRAP)
        check(preferences.edit().putString(KEY_DEBUG_KEY_DIGEST, encodedDigest).commit()) {
            "The debug key could not be saved."
        }
        backgroundStartedAt = null
        mutableState.value = AppLockState.Unlocked
    }

    private fun hasValidConfiguration(): Boolean {
        val hasDigest = preferences.contains(KEY_DEBUG_KEY_DIGEST)
        val hasKey = keyStore.containsAlias(KEY_ALIAS)
        if (hasDigest && !hasKey) {
            preferences.edit().remove(KEY_DEBUG_KEY_DIGEST).commit()
            return false
        }
        return hasDigest && hasKey
    }

    private fun digest(debugKey: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(getOrCreateSigningKey())
        return mac.doFinal(debugKey.toByteArray(Charsets.UTF_8))
    }

    private fun getOrCreateSigningKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(HMAC_ALGORITHM, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "app_lock"
        private const val KEY_DEBUG_KEY_DIGEST = "debug_key_digest"
        private const val KEY_ALIAS = "private_vault_debug_key_hmac"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val LOCK_TIMEOUT_MILLIS = 30L * 60L * 1_000L
        private const val DEFAULT_SCREEN_TIMEOUT_MILLIS = 30_000L
        private const val MIN_SCREEN_TIMEOUT_MILLIS = 5_000L
        private val DEBUG_KEY_PATTERN = Regex("^[0-9]{4}$")
    }
}

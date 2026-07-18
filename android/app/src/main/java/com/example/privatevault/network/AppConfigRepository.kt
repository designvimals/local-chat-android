package com.example.privatevault.network

import android.content.Context
import android.os.SystemClock
import com.example.privatevault.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Loads a small declarative configuration document. It never downloads or executes code.
 * A compiled configuration and the last validated response keep startup independent of the network.
 */
class AppConfigRepository(
    context: Context,
    private val bootstrapUrl: String = BuildConfig.APP_CONFIG_URL,
    private val compiledConfig: RemoteAppConfig = RemoteAppConfig.compiledDefaults(),
    private val client: HttpClient = defaultClient()
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val refreshMutex = Mutex()
    private val _config = MutableStateFlow(readCached() ?: compiledConfig)
    val config: StateFlow<RemoteAppConfig> = _config.asStateFlow()

    @Volatile
    private var lastRefreshElapsedMillis = Long.MIN_VALUE

    val current: RemoteAppConfig get() = _config.value

    suspend fun refresh(force: Boolean = false): RemoteAppConfig = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            if (!force && lastRefreshElapsedMillis != Long.MIN_VALUE &&
                now - lastRefreshElapsedMillis < MIN_REFRESH_INTERVAL_MILLIS
            ) {
                return@withLock current
            }
            lastRefreshElapsedMillis = now
            try {
                val response = client.get(bootstrapUrl) {
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.UserAgent, "Between-Android/${BuildConfig.VERSION_NAME}")
                }
                if (!response.status.isSuccess()) return@withLock current
                val raw = response.bodyAsText()
                if (raw.length > MAX_CONFIG_CHARACTERS) return@withLock current
                val decoded = runCatching { json.decodeFromString<RemoteAppConfig>(raw) }.getOrNull()
                    ?: return@withLock current
                val validated = RemoteAppConfigValidator.validated(decoded) ?: return@withLock current
                preferences.edit().putString(LAST_GOOD_CONFIG_KEY, json.encodeToString(validated)).apply()
                _config.value = validated
                validated
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                current
            }
        }
    }

    private fun readCached(): RemoteAppConfig? {
        val raw = preferences.getString(LAST_GOOD_CONFIG_KEY, null) ?: return null
        val decoded = runCatching { json.decodeFromString<RemoteAppConfig>(raw) }.getOrNull() ?: return null
        return RemoteAppConfigValidator.validated(decoded)
    }

    private companion object {
        const val PREFERENCES_NAME = "remote_app_config"
        const val LAST_GOOD_CONFIG_KEY = "last_good_config"
        const val MAX_CONFIG_CHARACTERS = 64 * 1024
        const val MIN_REFRESH_INTERVAL_MILLIS = 5 * 60_000L

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 3_500
                socketTimeoutMillis = 5_000
            }
        }
    }
}

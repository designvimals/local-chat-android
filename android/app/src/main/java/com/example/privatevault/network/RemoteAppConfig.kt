package com.example.privatevault.network

import com.example.privatevault.BuildConfig
import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class RemoteAppConfig(
    val schemaVersion: Int,
    val revision: Long,
    val relayBaseUrl: String,
    val release: RemoteReleaseConfig = RemoteReleaseConfig(),
    val features: RemoteFeatureConfig = RemoteFeatureConfig(),
    val limits: RemoteLimitConfig = RemoteLimitConfig(),
    val timing: RemoteTimingConfig = RemoteTimingConfig(),
    val motion: RemoteMotionConfig = RemoteMotionConfig()
) {
    companion object {
        fun compiledDefaults(relayBaseUrl: String = BuildConfig.BACKEND_URL): RemoteAppConfig = RemoteAppConfig(
            schemaVersion = SUPPORTED_SCHEMA_VERSION,
            revision = 0,
            relayBaseUrl = relayBaseUrl.trimEnd('/')
        )

        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

@Serializable
data class RemoteReleaseConfig(
    val latestVersion: String? = null,
    val minimumVersion: String? = null,
    val downloadUrl: String? = null
)

@Serializable
data class RemoteFeatureConfig(
    val fileSharing: Boolean = true,
    val messageSearch: Boolean = true
)

@Serializable
data class RemoteLimitConfig(
    val maxAttachmentBytes: Long? = null
)

@Serializable
data class RemoteTimingConfig(
    val connectionRetryMillis: Long = 2_000,
    val syncRecoveryMillis: Long = 60_000
)

@Serializable
data class RemoteMotionConfig(
    val sentMessageBounceScale: Float = 0.7f,
    val expressiveEffectsEnabled: Boolean = true
)

internal object RemoteAppConfigValidator {
    private val semanticVersion = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$")

    fun validated(config: RemoteAppConfig): RemoteAppConfig? {
        if (config.schemaVersion != RemoteAppConfig.SUPPORTED_SCHEMA_VERSION || config.revision < 1) return null
        val relayBaseUrl = normalizedHttpsOrigin(config.relayBaseUrl) ?: return null
        if (config.release.latestVersion != null && !semanticVersion.matches(config.release.latestVersion)) return null
        if (config.release.minimumVersion != null && !semanticVersion.matches(config.release.minimumVersion)) return null
        if (config.release.downloadUrl != null && normalizedHttpsUrl(config.release.downloadUrl) == null) return null
        if (config.limits.maxAttachmentBytes != null && config.limits.maxAttachmentBytes !in 1_048_576L..2_147_483_648L) {
            return null
        }
        if (config.timing.connectionRetryMillis !in 1_000L..60_000L) return null
        if (config.timing.syncRecoveryMillis !in 15_000L..900_000L) return null
        if (!config.motion.sentMessageBounceScale.isFinite() || config.motion.sentMessageBounceScale !in 0f..1f) return null

        return config.copy(
            relayBaseUrl = relayBaseUrl,
            release = config.release.copy(
                downloadUrl = config.release.downloadUrl?.let(::normalizedHttpsUrl)
            )
        )
    }

    private fun normalizedHttpsOrigin(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        if (uri.rawQuery != null || uri.rawFragment != null || uri.path !in setOf("", "/")) return null
        return URI("https", null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
    }

    private fun normalizedHttpsUrl(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return uri.toString()
    }
}

internal fun RemoteAppConfig.availableUpdate(installedVersion: String): AppUpdate? {
    val latest = release.latestVersion ?: return null
    val downloadUrl = release.downloadUrl ?: return null
    if (!GithubUpdateChecker.isNewer(latest, installedVersion)) return null
    return AppUpdate(version = latest, downloadUrl = downloadUrl)
}

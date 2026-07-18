package com.example.privatevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAppConfigTest {
    @Test
    fun `valid config is normalized and accepted`() {
        val config = validConfig().copy(relayBaseUrl = "https://relay.example/")

        val validated = RemoteAppConfigValidator.validated(config)

        assertEquals("https://relay.example", validated?.relayBaseUrl)
        assertEquals(0.7f, validated?.motion?.sentMessageBounceScale)
    }

    @Test
    fun `unsafe relay and invalid operational values are rejected`() {
        assertNull(RemoteAppConfigValidator.validated(validConfig().copy(relayBaseUrl = "http://relay.example")))
        assertNull(RemoteAppConfigValidator.validated(validConfig().copy(
            timing = RemoteTimingConfig(connectionRetryMillis = 20, syncRecoveryMillis = 60_000)
        )))
        assertNull(RemoteAppConfigValidator.validated(validConfig().copy(
            motion = RemoteMotionConfig(sentMessageBounceScale = 1.5f)
        )))
    }

    @Test
    fun `unknown schema is rejected while compiled defaults remain usable`() {
        assertNull(RemoteAppConfigValidator.validated(validConfig().copy(schemaVersion = 2)))
        assertTrue(RemoteAppConfig.compiledDefaults("http://10.0.2.2:8787").relayBaseUrl.startsWith("http://"))
    }

    @Test
    fun `remote release metadata becomes an update only when complete and newer`() {
        val update = validConfig().copy(
            release = RemoteReleaseConfig(
                latestVersion = "0.5.0",
                minimumVersion = "0.4.0",
                downloadUrl = "https://github.com/example/Between-v0.5.0.apk"
            )
        ).availableUpdate("0.4.5")

        assertEquals("0.5.0", update?.version)
        assertNull(validConfig().availableUpdate("0.4.5"))
    }

    private fun validConfig() = RemoteAppConfig(
        schemaVersion = 1,
        revision = 1,
        relayBaseUrl = "https://relay.example"
    )
}

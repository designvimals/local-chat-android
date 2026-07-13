package com.example.privatevault.server

import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.DeviceRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class LocalServerManager(
    private val settingsStore: SettingsStore,
    private val chatRepository: ChatRepository,
    private val pathResolver: PathResolver,
    private val deviceRepository: DeviceRepository
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Synchronized
    fun start(pairedToken: String) {
        if (server != null) return

        server = embeddedServer(CIO, host = "0.0.0.0", port = 8080) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
            }
            routing {
                healthRoutes(settingsStore, deviceRepository, pairedToken)
                chatRoutes(chatRepository, pairedToken)
                storageRoutes(pathResolver, settingsStore, pairedToken)
            }
        }.start(wait = false)
    }

    @Synchronized
    fun stop() {
        server?.stop(1000, 3000)
        server = null
    }
}

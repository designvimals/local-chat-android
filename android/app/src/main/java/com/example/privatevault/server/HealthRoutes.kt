package com.example.privatevault.server

import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.repository.DeviceRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes(settingsStore: SettingsStore, deviceRepository: DeviceRepository, pairedToken: String) {
    get("/health") {
        if (!call.requireBearerToken(pairedToken)) return@get
        call.respond(
            HealthResponse(
                status = "ok",
                deviceName = deviceRepository.deviceName,
                storageSharingEnabled = settingsStore.storageSharingEnabled.first()
            )
        )
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val deviceName: String,
    val storageSharingEnabled: Boolean
)

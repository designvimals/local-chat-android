package com.example.privatevault.network

import android.util.Base64
import com.example.privatevault.BuildConfig
import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.DeviceRepository
import com.example.privatevault.model.Message
import com.example.privatevault.server.PathResolver
import com.example.privatevault.util.FileUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface BackendRegistrationState {
    data object Idle : BackendRegistrationState
    data object Connecting : BackendRegistrationState
    data class Registered(val endpointUrl: String) : BackendRegistrationState
    data class Failed(val message: String) : BackendRegistrationState
}

/** Maintains the phone's outbound-only connection to the public relay. */
class BackendClient(
    private val tokenStore: TokenStore,
    private val deviceRepository: DeviceRepository,
    private val chatRepository: ChatRepository,
    private val pathResolver: PathResolver,
    private val settingsStore: SettingsStore,
    private val baseUrl: String = BuildConfig.BACKEND_URL
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
            maxFrameSize = 2L * 1024L * 1024L
        }
    }
    private val _registrationState = MutableStateFlow<BackendRegistrationState>(BackendRegistrationState.Idle)
    val registrationState: StateFlow<BackendRegistrationState> = _registrationState

    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null

    suspend fun connectRelay(storageSharingEnabled: Boolean) {
        _registrationState.value = BackendRegistrationState.Connecting
        try {
            client.webSocket(urlString = relayUrl()) {
                activeSession = this
                sendJson(deviceRegistration(storageSharingEnabled))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        handleRelayMessage(this, frame.readText())
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _registrationState.value = BackendRegistrationState.Failed(
                "Cannot reach the relay at $baseUrl. The relay must be deployed on a public HTTPS address."
            )
        } finally {
            activeSession = null
            if (_registrationState.value is BackendRegistrationState.Registered) {
                _registrationState.value = BackendRegistrationState.Failed("Relay disconnected. Reconnecting…")
            }
        }
    }

    suspend fun updateStorageSharing(enabled: Boolean) {
        activeSession?.sendJson(
            buildJsonObject {
                put("type", "device.update")
                put("storageSharingEnabled", enabled)
            }
        )
    }

    suspend fun restartConnection() {
        activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Reconnect requested"))
    }

    private suspend fun handleRelayMessage(session: DefaultClientWebSocketSession, raw: String) {
        val message = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (message.string("type")) {
            "device.registered" -> {
                _registrationState.value = BackendRegistrationState.Registered(baseUrl)
            }
            "pairing.claimed" -> {
                tokenStore.markPairingClaimed()
                chatRepository.markViewerConnectedFromRelay()
            }
            "chat.sync" -> respond(session, message) {
                val readerDeviceId = message.payload()["readerDeviceId"]?.jsonPrimitive?.contentOrNull
                    ?: com.example.privatevault.data.local.MessageStore.VIEWER_DEVICE_ID
                buildJsonObject {
                    put("messages", json.encodeToJsonElement(chatRepository.messagesForViewer(readerDeviceId)))
                }
            }
            "chat.send" -> respond(session, message) {
                val incoming = json.decodeFromJsonElement<Message>(message.payload().getValue("message"))
                val saved = chatRepository.receiveMessage(
                    incoming.id,
                    incoming.senderDeviceId,
                    incoming.text,
                    incoming.timestamp
                )
                buildJsonObject { put("message", json.encodeToJsonElement(saved)) }
            }
            "chat.read" -> respond(session, message) {
                val payload = message.payload()
                chatRepository.markReadBy(
                    payload.string("readerDeviceId"),
                    payload.string("readAt")
                )
                buildJsonObject { put("ok", true) }
            }
            "device.status.get" -> respond(session, message) {
                buildJsonObject {
                    put("online", true)
                    put("storageSharingEnabled", storageAllowed())
                    put("deviceName", deviceRepository.deviceName)
                }
            }
            "storage.list" -> respond(session, message) {
                require(storageAllowed()) { "File sharing is paused on the phone." }
                val path = message.payload().string("path")
                buildJsonObject {
                    put("path", path)
                    put("items", json.encodeToJsonElement(pathResolver.list(path)))
                }
            }
            "storage.download" -> streamDownload(session, message)
        }
    }

    private suspend fun streamDownload(session: DefaultClientWebSocketSession, command: JsonObject) {
        val requestId = command.string("requestId")
        try {
            require(storageAllowed()) { "File sharing is paused on the phone." }
            val file = pathResolver.resolveFile(command.payload().string("path"))
            session.sendJson(
                buildJsonObject {
                    put("type", "download.start")
                    put("requestId", requestId)
                    put("name", file.name)
                    put("mimeType", FileUtils.mimeType(file))
                    put("size", file.length())
                }
            )
            streamFileChunks(session, requestId, file)
            session.sendJson(
                buildJsonObject {
                    put("type", "download.complete")
                    put("requestId", requestId)
                }
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            sendError(session, requestId, error.message ?: "Download failed on the phone.")
        }
    }

    private suspend fun streamFileChunks(session: DefaultClientWebSocketSession, requestId: String, file: File) {
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(48 * 1024)
            var chunksSent = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val encoded = Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP)
                session.sendJson(
                    buildJsonObject {
                        put("type", "download.chunk")
                        put("requestId", requestId)
                        put("data", encoded)
                    }
                )
                chunksSent += 1
                if (chunksSent % 8 == 0) yield()
            }
        }
    }

    private suspend fun respond(
        session: DefaultClientWebSocketSession,
        command: JsonObject,
        action: suspend () -> JsonObject
    ) {
        val requestId = command.string("requestId")
        try {
            val payload = action()
            session.sendJson(
                buildJsonObject {
                    put("type", "response")
                    put("requestId", requestId)
                    put("ok", true)
                    put("payload", payload)
                }
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            sendError(session, requestId, error.message ?: "The phone could not complete that request.")
        }
    }

    private suspend fun sendError(session: DefaultClientWebSocketSession, requestId: String, error: String) {
        session.sendJson(
            buildJsonObject {
                put("type", "response")
                put("requestId", requestId)
                put("ok", false)
                put("error", error)
            }
        )
    }

    private suspend fun storageAllowed(): Boolean {
        return settingsStore.storageSharingEnabled.first() && settingsStore.storagePermissionGranted.first()
    }

    private fun deviceRegistration(storageSharingEnabled: Boolean): JsonObject {
        return buildJsonObject {
            put("type", "register.device")
            put("registrationKey", BuildConfig.REGISTRATION_KEY)
            put("deviceId", deviceRepository.deviceId)
            put("deviceName", deviceRepository.deviceName)
            put("pairingCode", tokenStore.getPairingCode())
            put("accessToken", tokenStore.getAccessToken())
            put("pairingAvailable", !tokenStore.isPairingClaimed())
            put("storageSharingEnabled", storageSharingEnabled)
        }
    }

    private fun relayUrl(): String {
        return when {
            baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://").trimEnd('/')}/relay"
            baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://").trimEnd('/')}/relay"
            else -> baseUrl.trimEnd('/') + "/relay"
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendJson(message: JsonObject) {
        send(Frame.Text(json.encodeToString(message)))
    }

    private fun JsonObject.payload(): JsonObject = getValue("payload").jsonObject

    private fun JsonObject.string(name: String): String {
        return get(name)?.jsonPrimitive?.contentOrNull ?: error("Missing $name")
    }
}

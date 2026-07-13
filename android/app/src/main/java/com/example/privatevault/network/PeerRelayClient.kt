package com.example.privatevault.network

import android.util.Base64
import com.example.privatevault.attachment.AttachmentManager
import com.example.privatevault.BuildConfig
import com.example.privatevault.data.local.PeerConnection
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.data.repository.DeviceRepository
import com.example.privatevault.model.Message
import com.example.privatevault.model.ChatAttachment
import com.example.privatevault.util.TimeUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed interface PeerConnectionState {
    data object Disconnected : PeerConnectionState
    data object Connecting : PeerConnectionState
    data class Connected(val friendName: String) : PeerConnectionState
    data class Failed(val message: String) : PeerConnectionState
}

class PeerRelayClient(
    private val tokenStore: TokenStore,
    private val deviceRepository: DeviceRepository,
    private val chatRepository: ChatRepository,
    private val attachmentManager: AttachmentManager,
    private val baseUrl: String = BuildConfig.BACKEND_URL
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
            maxFrameSize = 2L * 1024L * 1024L
        }
    }
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val downloads = ConcurrentHashMap<String, PendingAttachmentDownload>()
    private val uploadedAttachments = ConcurrentHashMap.newKeySet<String>()
    private val syncMutex = Mutex()
    private val _state = MutableStateFlow<PeerConnectionState>(PeerConnectionState.Disconnected)
    val state: StateFlow<PeerConnectionState> = _state

    @Volatile
    private var activeSession: DefaultClientWebSocketSession? = null

    @Volatile
    private var peerOnline = false

    suspend fun claimPairingCode(code: String): Result<PeerConnection> = withContext(Dispatchers.IO) {
        runCatching {
            require(code.matches(Regex("\\d{6}"))) { "Enter a six-digit code." }
            require(code != tokenStore.getPairingCode()) { "Enter the code from the other phone." }
            val response = client.post("${baseUrl.trimEnd('/')}/auth/login") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(json.encodeToString(buildJsonObject {
                    put("pairingCode", code)
                    put("clientType", "android")
                    put("viewerDeviceId", deviceRepository.deviceId)
                    put("deviceName", deviceRepository.deviceName)
                }))
            }
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            if (!response.status.isSuccess()) {
                error(body["error"]?.jsonPrimitive?.contentOrNull ?: "The code could not be paired.")
            }
            val connection = PeerConnection(
                accessToken = body.requireString("pairedToken"),
                viewerDeviceId = body.requireString("viewerDeviceId"),
                friendName = body.requireString("friendName")
            )
            tokenStore.savePeerConnection(connection)
            connection
        }
    }

    suspend fun connectPeer(connection: PeerConnection) {
        _state.value = PeerConnectionState.Connecting
        try {
            client.webSocket(urlString = relayUrl()) {
                activeSession = this
                sendJson(buildJsonObject {
                    put("type", "register.viewer")
                    put("accessToken", connection.accessToken)
                })
                coroutineScope {
                    val receiver = launch { receiveLoop(connection.friendName) }
                    val synchronizer = launch {
                        while (isActive) {
                            if (peerOnline) runCatching { syncOnce(this@webSocket, connection) }
                            delay(2_500)
                        }
                    }
                    receiver.join()
                    synchronizer.cancel()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            _state.value = PeerConnectionState.Failed(error.message ?: "The paired phone is unreachable.")
        } finally {
            activeSession = null
            peerOnline = false
            chatRepository.setPeerConnected(false)
            pending.values.forEach { it.cancel() }
            pending.clear()
            downloads.values.forEach { it.completion.cancel() }
            downloads.clear()
        }
    }

    suspend fun restartConnection() {
        activeSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Peer reconnect requested"))
    }

    suspend fun disconnect() {
        tokenStore.clearPeerConnection()
        restartConnection()
        _state.value = PeerConnectionState.Disconnected
        chatRepository.setPeerConnected(false)
    }

    private suspend fun DefaultClientWebSocketSession.receiveLoop(friendName: String) {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val message = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull() ?: continue
            when (message["type"]?.jsonPrimitive?.contentOrNull) {
                "viewer.registered" -> {
                    peerOnline = message["online"]?.jsonPrimitive?.contentOrNull == "true"
                    if (peerOnline) {
                        _state.value = PeerConnectionState.Connected(friendName)
                        chatRepository.setPeerConnected(true)
                    }
                }
                "device.status" -> {
                    peerOnline = message["online"]?.jsonPrimitive?.contentOrNull == "true"
                    _state.value = if (peerOnline) PeerConnectionState.Connected(friendName) else PeerConnectionState.Connecting
                    chatRepository.setPeerConnected(peerOnline)
                }
                "response" -> {
                    val requestId = message["requestId"]?.jsonPrimitive?.contentOrNull ?: continue
                    val download = downloads.remove(requestId)
                    if (download != null) {
                        attachmentManager.discardIncoming(download.attachment.id)
                        download.completion.completeExceptionally(
                            IllegalStateException(message["error"]?.jsonPrimitive?.contentOrNull ?: "Attachment transfer failed.")
                        )
                        continue
                    }
                    pending.remove(requestId)?.complete(message)
                }
                "download.start" -> {
                    val requestId = message["requestId"]?.jsonPrimitive?.contentOrNull ?: continue
                    downloads[requestId]?.let { attachmentManager.beginIncoming(it.attachment) }
                }
                "download.chunk" -> {
                    val requestId = message["requestId"]?.jsonPrimitive?.contentOrNull ?: continue
                    val data = message["data"]?.jsonPrimitive?.contentOrNull ?: continue
                    downloads[requestId]?.let {
                        attachmentManager.appendIncomingChunk(it.attachment.id, Base64.decode(data, Base64.DEFAULT))
                    }
                }
                "download.complete" -> {
                    val requestId = message["requestId"]?.jsonPrimitive?.contentOrNull ?: continue
                    downloads.remove(requestId)?.let { download ->
                        runCatching { attachmentManager.finishIncoming(download.attachment) }
                            .onSuccess { download.completion.complete(Unit) }
                            .onFailure(download.completion::completeExceptionally)
                    }
                }
            }
        }
    }

    private suspend fun syncOnce(session: DefaultClientWebSocketSession, connection: PeerConnection) {
        syncMutex.withLock {
            val firstSync = request(session, "chat.sync", buildJsonObject {
                put("readerDeviceId", deviceRepository.deviceId)
            })
            downloadMissingAttachments(session, mergeResponseMessages(firstSync))

            request(session, "chat.typing", buildJsonObject {
                put("typing", chatRepository.isLocalTyping())
            })

            for (message in chatRepository.pendingForPeer()) {
                message.attachment?.takeUnless { uploadedAttachments.contains(it.id) }?.let { attachment ->
                    uploadAttachment(session, attachment)
                    uploadedAttachments.add(attachment.id)
                }
                val response = request(session, "chat.send", buildJsonObject {
                    put("message", json.encodeToJsonElement(message))
                })
                val saved = response["message"]?.let { json.decodeFromJsonElement<Message>(it) }
                if (saved != null) chatRepository.mergeFromPeer(listOf(saved))
                else chatRepository.markPeerMessageDelivered(message.id, TimeUtils.nowIso())
            }

            request(session, "chat.read", buildJsonObject {
                put("readerDeviceId", deviceRepository.deviceId)
                put("readAt", TimeUtils.nowIso())
            })
            downloadMissingAttachments(session, mergeResponseMessages(request(session, "chat.sync", buildJsonObject {
                put("readerDeviceId", deviceRepository.deviceId)
            })))
        }
    }

    private fun mergeResponseMessages(payload: JsonObject): List<Message> {
        chatRepository.setRemoteTyping(payload["typing"]?.jsonPrimitive?.contentOrNull == "true")
        val messages = payload["messages"]?.jsonArray?.map { json.decodeFromJsonElement<Message>(it) }.orEmpty()
        chatRepository.mergeFromPeer(messages)
        return messages
    }

    private suspend fun uploadAttachment(session: DefaultClientWebSocketSession, attachment: ChatAttachment) {
        request(session, "chat.attachment.upload.start", buildJsonObject {
            put("attachment", json.encodeToJsonElement(attachment))
        })
        attachmentManager.open(attachment.id).buffered().use { input ->
            val buffer = ByteArray(48 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                request(session, "chat.attachment.upload.chunk", buildJsonObject {
                    put("attachmentId", attachment.id)
                    put("data", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP))
                })
            }
        }
        request(session, "chat.attachment.upload.complete", buildJsonObject {
            put("attachment", json.encodeToJsonElement(attachment))
        })
    }

    private suspend fun downloadMissingAttachments(session: DefaultClientWebSocketSession, messages: List<Message>) {
        messages.asSequence()
            .filter { it.senderDeviceId != deviceRepository.deviceId }
            .mapNotNull(Message::attachment)
            .filterNot { attachmentManager.hasLocalBytes(it.id) }
            .distinctBy(ChatAttachment::id)
            .forEach { downloadAttachment(session, it) }
    }

    private suspend fun downloadAttachment(session: DefaultClientWebSocketSession, attachment: ChatAttachment) {
        val requestId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Unit>()
        downloads[requestId] = PendingAttachmentDownload(attachment, completion)
        session.sendJson(buildJsonObject {
            put("type", "chat.attachment.download")
            put("requestId", requestId)
            put("payload", buildJsonObject { put("attachmentId", attachment.id) })
        })
        try {
            withTimeout(10 * 60_000) { completion.await() }
        } catch (error: Throwable) {
            attachmentManager.discardIncoming(attachment.id)
            throw error
        } finally {
            downloads.remove(requestId)
        }
    }

    private suspend fun request(
        session: DefaultClientWebSocketSession,
        type: String,
        payload: JsonObject
    ): JsonObject {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pending[requestId] = deferred
        session.sendJson(buildJsonObject {
            put("type", type)
            put("requestId", requestId)
            put("payload", payload)
        })
        val response = try {
            withTimeout(120_000) { deferred.await() }
        } finally {
            pending.remove(requestId)
        }
        if (response["ok"]?.jsonPrimitive?.contentOrNull != "true") {
            error(response["error"]?.jsonPrimitive?.contentOrNull ?: "The paired phone did not complete the request.")
        }
        return response["payload"]?.jsonObject ?: buildJsonObject { }
    }

    private fun relayUrl(): String = when {
        baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://").trimEnd('/')}/relay"
        baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://").trimEnd('/')}/relay"
        else -> baseUrl.trimEnd('/') + "/relay"
    }

    private suspend fun DefaultClientWebSocketSession.sendJson(message: JsonObject) {
        send(Frame.Text(json.encodeToString(message)))
    }

    private fun JsonObject.requireString(name: String): String {
        return get(name)?.jsonPrimitive?.contentOrNull ?: error("Missing $name")
    }

    private data class PendingAttachmentDownload(
        val attachment: ChatAttachment,
        val completion: CompletableDeferred<Unit>
    )
}

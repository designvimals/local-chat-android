package com.example.privatevault.server

import com.example.privatevault.data.repository.ChatRepository
import com.example.privatevault.model.Message
import kotlinx.serialization.Serializable
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.chatRoutes(chatRepository: ChatRepository, pairedToken: String) {
    route("/chat") {
        get("/messages") {
            if (!call.requireBearerToken(pairedToken)) return@get
            call.respond(MessageListResponse(chatRepository.messagesForViewer()))
        }

        post("/messages") {
            if (!call.requireBearerToken(pairedToken)) return@post
            val body = call.receive<SendMessageRequest>()
            val text = body.text.trim()
            if (text.isBlank() || text.length > 4000 || body.id.isBlank() || body.senderDeviceId.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Type a message before sending."))
                return@post
            }
            val message = chatRepository.receiveMessage(body.id, body.senderDeviceId, text, body.timestamp)
            call.respond(HttpStatusCode.Created, SendMessageResponse(ok = true, messageId = message.id))
        }

        post("/read") {
            if (!call.requireBearerToken(pairedToken)) return@post
            val body = call.receive<ReadReceiptRequest>()
            chatRepository.markReadBy(body.readerDeviceId, body.readAt)
            call.respond(mapOf("ok" to true))
        }
    }
}

@Serializable
data class MessageListResponse(val messages: List<Message>)

@Serializable
data class SendMessageRequest(
    val id: String,
    val senderDeviceId: String,
    val text: String,
    val timestamp: String
)

@Serializable
data class ReadReceiptRequest(val readerDeviceId: String, val readAt: String)

@Serializable
data class SendMessageResponse(val ok: Boolean, val messageId: String)

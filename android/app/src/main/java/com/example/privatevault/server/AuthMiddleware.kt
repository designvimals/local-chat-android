package com.example.privatevault.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.requireBearerToken(expectedToken: String): Boolean {
    val header = request.headers[HttpHeaders.Authorization]
    val token = header?.removePrefix("Bearer ")?.trim()
    if (token == expectedToken) {
        return true
    }

    respond(HttpStatusCode.Unauthorized, mapOf("error" to "Storage access is not available."))
    return false
}

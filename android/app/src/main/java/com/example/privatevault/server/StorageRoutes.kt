package com.example.privatevault.server

import com.example.privatevault.data.local.SettingsStore
import com.example.privatevault.model.FileItem
import com.example.privatevault.service.StorageSessionNotifier
import com.example.privatevault.util.FileUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.storageRoutes(
    pathResolver: PathResolver,
    settingsStore: SettingsStore,
    notifier: StorageSessionNotifier,
    pairedToken: String
) {
    route("/storage") {
        get("/list") {
            if (!call.requireBearerToken(pairedToken)) return@get
            if (!settingsStore.storageSharingEnabled.first()) {
                call.respond(HttpStatusCode(423, "Sharing paused"), mapOf("error" to "Storage sharing is paused."))
                return@get
            }

            notifier.markActive()
            val path = call.request.queryParameters["path"] ?: "/"
            val items = runCatching { pathResolver.list(path) }
                .getOrElse {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "That folder is not shared."))
                    return@get
                }
            call.respond(StorageListResponse(path = path, items = items))
        }

        get("/download") {
            if (!call.requireBearerToken(pairedToken)) return@get
            if (!settingsStore.storageSharingEnabled.first()) {
                call.respond(HttpStatusCode(423, "Sharing paused"), mapOf("error" to "Storage sharing is paused."))
                return@get
            }

            notifier.markActive()
            val path = call.request.queryParameters["path"]
            if (path.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Choose a file to download."))
                return@get
            }

            val file = runCatching { pathResolver.resolveFile(path) }
                .getOrElse {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "That file is not available."))
                    return@get
                }

            val safeName = file.name.replace("\"", "")
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$safeName\"")
            call.respond(LocalFileContent(file, contentType = ContentType.parse(FileUtils.mimeType(file))))
        }
    }
}

@Serializable
data class StorageListResponse(
    val path: String,
    val items: List<FileItem>
)

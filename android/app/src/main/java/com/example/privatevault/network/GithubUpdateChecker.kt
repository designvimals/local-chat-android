package com.example.privatevault.network

import com.example.privatevault.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AppUpdate(
    val version: String,
    val downloadUrl: String
)

object GithubUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/designvimals/local-chat-android/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findAvailableUpdate(): AppUpdate? {
        val client = HttpClient(CIO)
        return try {
            val response = client.get(LATEST_RELEASE_URL) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "Between-Android/${BuildConfig.VERSION_NAME}")
                header("X-GitHub-Api-Version", "2026-03-10")
            }
            if (response.status.value !in 200..299) return null

            val release = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            val version = tag.removePrefix("v")
            if (!isNewer(version, BuildConfig.VERSION_NAME)) return null

            val apk = release["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { asset ->
                    asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true
                }
                ?: return null
            val downloadUrl = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
            AppUpdate(version = version, downloadUrl = downloadUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            client.close()
        }
    }

    internal fun isNewer(candidate: String, installed: String): Boolean {
        val candidateParts = versionParts(candidate)
        val installedParts = versionParts(installed)
        val length = maxOf(candidateParts.size, installedParts.size)
        for (index in 0 until length) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val installedPart = installedParts.getOrElse(index) { 0 }
            if (candidatePart != installedPart) return candidatePart > installedPart
        }
        return false
    }

    private fun versionParts(version: String): List<Int> = version
        .removePrefix("v")
        .split('.')
        .map { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}

package com.example.privatevault.ui.screen.chat

import android.text.Html
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LinkPreview(
    val url: String,
    val host: String,
    val title: String,
    val description: String?
)

object LinkPreviewLoader {
    private const val MAX_HTML_CHARS = 256 * 1024
    private const val MAX_REDIRECTS = 3
    private val cache = ConcurrentHashMap<String, LinkPreview>()
    private val urlPattern = Regex("""https?://[^\s<>()]+""", RegexOption.IGNORE_CASE)
    private val metaTagPattern = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attributePattern = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*[\"']([^\"']*)[\"']""")
    private val titlePattern = Regex(
        """<title\b[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val tagPattern = Regex("""<[^>]+>""")

    fun firstUrl(text: String): String? = urlPattern.find(text)?.value
        ?.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}')

    fun fallback(url: String): LinkPreview {
        val host = runCatching { URI(url).host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { url }
        return LinkPreview(url = url, host = host, title = host, description = null)
    }

    suspend fun load(url: String): LinkPreview = cache[url] ?: withContext(Dispatchers.IO) {
        cache[url] ?: runCatching { loadRemote(url) }
            .getOrElse { fallback(url) }
            .also { cache[url] = it }
    }

    private fun loadRemote(originalUrl: String): LinkPreview {
        var current = URL(originalUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            require(current.protocol == "http" || current.protocol == "https")
            requirePublicHost(current.host)

            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", "en")
                setRequestProperty("Range", "bytes=0-${MAX_HTML_CHARS - 1}")
                setRequestProperty("User-Agent", "PrivateVault-LinkPreview/1.0")
            }

            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    require(redirectCount < MAX_REDIRECTS)
                    val location = connection.getHeaderField("Location") ?: error("Redirect without location")
                    current = URL(current, location)
                    return@repeat
                }
                require(status in 200..299)
                val contentType = connection.contentType.orEmpty().lowercase()
                require(contentType.isBlank() || contentType.startsWith("text/html") || contentType.startsWith("application/xhtml+xml"))
                val declaredLength = connection.contentLengthLong
                require(declaredLength <= 1_048_576 || declaredLength == -1L)

                val html = readLimitedHtml(connection)
                val metadata = parseMetadata(html)
                val fallback = fallback(current.toString())
                return fallback.copy(
                    url = originalUrl,
                    title = metadata.title ?: fallback.title,
                    description = metadata.description
                )
            } finally {
                connection.disconnect()
            }
        }
        return fallback(originalUrl)
    }

    private fun readLimitedHtml(connection: HttpURLConnection): String {
        val output = StringBuilder()
        InputStreamReader(connection.inputStream, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8_192)
            while (output.length < MAX_HTML_CHARS) {
                val remaining = minOf(buffer.size, MAX_HTML_CHARS - output.length)
                val read = reader.read(buffer, 0, remaining)
                if (read <= 0) break
                output.append(buffer, 0, read)
            }
        }
        return output.toString()
    }

    private fun parseMetadata(html: String): Metadata {
        val values = mutableMapOf<String, String>()
        metaTagPattern.findAll(html).forEach { match ->
            val attributes = attributePattern.findAll(match.value).associate {
                it.groupValues[1].lowercase() to it.groupValues[2]
            }
            val key = attributes["property"]?.lowercase() ?: attributes["name"]?.lowercase()
            val content = attributes["content"]
            if (!key.isNullOrBlank() && !content.isNullOrBlank()) values.putIfAbsent(key, content)
        }
        val documentTitle = titlePattern.find(html)?.groupValues?.getOrNull(1)
        val title = firstNonBlank(values["og:title"], values["twitter:title"], documentTitle)
            ?.cleanHtml()
            ?.take(140)
        val description = firstNonBlank(
            values["og:description"],
            values["twitter:description"],
            values["description"]
        )?.cleanHtml()?.take(240)
        return Metadata(title = title, description = description)
    }

    private fun requirePublicHost(host: String) {
        val normalized = host.lowercase().trimEnd('.')
        require(normalized.isNotBlank())
        require(normalized != "localhost" && !normalized.endsWith(".local") && !normalized.endsWith(".internal"))
        val addresses = InetAddress.getAllByName(normalized)
        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress))
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        if (address is Inet6Address) {
            val first = address.address.first().toInt() and 0xff
            if (first and 0xfe == 0xfc) return false
        }
        return true
    }

    private fun String.cleanHtml(): String = Html.fromHtml(
        tagPattern.replace(this, " "),
        Html.FROM_HTML_MODE_LEGACY
    ).toString().replace(Regex("""\s+"""), " ").trim()

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private data class Metadata(val title: String?, val description: String?)
}

package com.example.privatevault.util

object PathUtils {
    fun normalizeVirtualPath(path: String): String? {
        if (path.isBlank()) return "/"
        val withSlash = if (path.startsWith("/")) path else "/$path"
        val parts = withSlash.split("/")
            .filter { it.isNotBlank() && it != "." }

        if (parts.any { it == ".." || it.contains('\u0000') }) {
            return null
        }

        return if (parts.isEmpty()) "/" else "/${parts.joinToString("/")}"
    }

    fun parent(path: String): String {
        val normalized = normalizeVirtualPath(path) ?: return "/"
        if (normalized == "/") return "/"
        val parts = normalized.split("/").filter { it.isNotBlank() }.dropLast(1)
        return if (parts.isEmpty()) "/" else "/${parts.joinToString("/")}"
    }
}

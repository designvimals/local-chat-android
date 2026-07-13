package com.example.privatevault.server

import android.os.Environment
import com.example.privatevault.model.FileItem
import com.example.privatevault.util.FileUtils
import com.example.privatevault.util.PathUtils
import java.io.File
import java.nio.file.NoSuchFileException
import java.time.Instant

class PathResolver {
    private val storageRoot: File = Environment.getExternalStorageDirectory()

    private val roots: Map<String, File>
        get() {
            val publicFolders = storageRoot.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && it.canRead() && !it.isHidden && it.name != "Android" }
                ?.associateBy { "/${it.name}" }
                .orEmpty()
                .toMutableMap()

            val appMedia = File(storageRoot, "Android/media")
            if (appMedia.isDirectory && appMedia.canRead()) {
                publicFolders["/App media"] = appMedia
            }
            return publicFolders
        }

    fun list(virtualPath: String): List<FileItem> {
        val normalized = PathUtils.normalizeVirtualPath(virtualPath) ?: throw SecurityException("Denied path")
        if (normalized == "/") {
            return roots.keys.sortedBy(String::lowercase).map { root ->
                FileItem(
                    name = root.removePrefix("/"),
                    path = root,
                    type = "folder",
                    size = null,
                    mimeType = null,
                    lastModified = null
                )
            }
        }

        val resolved = resolve(normalized)
        val file = resolved.file
        if (!file.exists() || !file.isDirectory) {
            return emptyList()
        }

        return file.listFiles()
            ?.asSequence()
            ?.filter { child -> !child.isHidden && child.canRead() }
            ?.filterNot { child -> child.name == "Android" }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { child -> child.toFileItem("${resolved.virtualPath}/${child.name}".replace("//", "/")) }
            ?.toList()
            .orEmpty()
    }

    fun resolveFile(virtualPath: String): File {
        val normalized = PathUtils.normalizeVirtualPath(virtualPath) ?: throw SecurityException("Denied path")
        val resolved = resolve(normalized)
        if (!resolved.file.exists() || !resolved.file.isFile) {
            throw NoSuchFileException(resolved.file)
        }
        return resolved.file
    }

    private fun resolve(virtualPath: String): ResolvedPath {
        val parts = virtualPath.split("/").filter { it.isNotBlank() }
        val rootName = "/${parts.firstOrNull() ?: throw SecurityException("Missing root")}"
        val root = roots[rootName] ?: throw SecurityException("Denied root")
        val relative = parts.drop(1).joinToString(File.separator)
        val target = if (relative.isBlank()) root else File(root, relative)

        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (canonicalTarget != canonicalRoot && !canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
            throw SecurityException("Denied path")
        }

        return ResolvedPath(canonicalTarget, virtualPath)
    }

    private fun File.toFileItem(virtualPath: String): FileItem {
        return FileItem(
            name = name,
            path = virtualPath,
            type = if (isDirectory) "folder" else "file",
            size = if (isFile) length() else null,
            mimeType = if (isFile) FileUtils.mimeType(this) else null,
            lastModified = runCatching { Instant.ofEpochMilli(lastModified()).toString() }.getOrNull()
        )
    }

    private data class ResolvedPath(
        val file: File,
        val virtualPath: String
    )
}

package com.example.privatevault.data.repository

import com.example.privatevault.model.FileItem
import com.example.privatevault.server.PathResolver

class StorageRepository(private val pathResolver: PathResolver) {
    fun list(path: String): List<FileItem> = pathResolver.list(path)
}

package com.example.privatevault.network

import com.example.privatevault.model.FileItem

class ApiClient {
    suspend fun listRemoteStorage(path: String): List<FileItem> {
        return emptyList()
    }
}

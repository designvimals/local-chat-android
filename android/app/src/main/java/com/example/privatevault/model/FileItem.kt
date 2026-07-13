package com.example.privatevault.model

import kotlinx.serialization.Serializable

@Serializable
data class FileItem(
    val name: String,
    val path: String,
    val type: String,
    val size: Long?,
    val mimeType: String?,
    val lastModified: String?
)

package com.example.privatevault.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long
)

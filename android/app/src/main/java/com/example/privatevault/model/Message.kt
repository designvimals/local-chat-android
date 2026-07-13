package com.example.privatevault.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val senderDeviceId: String,
    val receiverDeviceId: String,
    val text: String,
    val timestamp: String,
    val status: String,
    val deliveredAt: String? = null,
    val readAt: String? = null
)

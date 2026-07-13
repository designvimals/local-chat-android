package com.example.privatevault.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val sessionId: String,
    val viewerDeviceId: String,
    val storageOwnerDeviceId: String,
    val startedAt: String,
    val lastActiveAt: String,
    val status: String
)

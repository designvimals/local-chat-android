package com.example.privatevault.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val deviceId: String,
    val deviceName: String,
    val role: String,
    val pairedToken: String,
    val lastSeen: String,
    val storageSharingEnabled: Boolean,
    val currentEndpoint: String?,
    val createdAt: String
)

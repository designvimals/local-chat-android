package com.example.privatevault.data.repository

import android.os.Build
import com.example.privatevault.data.local.TokenStore

class DeviceRepository(private val tokenStore: TokenStore) {
    val deviceId: String
        get() = tokenStore.getDeviceId()

    val deviceName: String
        get() = "${Build.MANUFACTURER.replaceFirstChar { it.titlecase() }} ${Build.MODEL}".trim()
}

package com.example.privatevault.ui.screen.pairing

import androidx.lifecycle.ViewModel
import com.example.privatevault.data.local.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PairingViewModel(private val tokenStore: TokenStore) : ViewModel() {
    private val _pairingCode = MutableStateFlow(tokenStore.getPairingCode())
    val pairingCode: StateFlow<String> = _pairingCode
    private val _pairingAvailable = MutableStateFlow(!tokenStore.isPairingClaimed())
    val pairingAvailable: StateFlow<Boolean> = _pairingAvailable

    fun currentToken(): String = _pairingCode.value

    fun rotate(onRotated: () -> Unit) {
        _pairingCode.value = tokenStore.rotatePairingCode()
        _pairingAvailable.value = true
        onRotated()
    }

    fun refresh() {
        _pairingCode.value = tokenStore.getPairingCode()
        _pairingAvailable.value = !tokenStore.isPairingClaimed()
    }
}

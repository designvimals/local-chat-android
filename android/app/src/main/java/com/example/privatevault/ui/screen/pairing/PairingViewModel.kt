package com.example.privatevault.ui.screen.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatevault.data.local.TokenStore
import com.example.privatevault.network.PeerConnectionState
import com.example.privatevault.network.PeerRelayClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PairingViewModel(
    private val tokenStore: TokenStore,
    private val peerRelayClient: PeerRelayClient,
    private val onPeerChanged: () -> Unit
) : ViewModel() {
    private val _pairingCode = MutableStateFlow(tokenStore.getPairingCode())
    val pairingCode: StateFlow<String> = _pairingCode
    private val _pairingAvailable = MutableStateFlow(!tokenStore.isPairingClaimed())
    val pairingAvailable: StateFlow<Boolean> = _pairingAvailable
    val peerState: StateFlow<PeerConnectionState> = peerRelayClient.state
    private val _peerName = MutableStateFlow(tokenStore.getPeerConnection()?.friendName)
    val peerName: StateFlow<String?> = _peerName
    private val _claimingPeer = MutableStateFlow(false)
    val claimingPeer: StateFlow<Boolean> = _claimingPeer
    private val _peerError = MutableStateFlow<String?>(null)
    val peerError: StateFlow<String?> = _peerError

    fun currentToken(): String = _pairingCode.value

    fun rotate(onRotated: () -> Unit) {
        _pairingCode.value = tokenStore.rotatePairingCode()
        _pairingAvailable.value = true
        onRotated()
    }

    fun refresh() {
        _pairingCode.value = tokenStore.getPairingCode()
        _pairingAvailable.value = !tokenStore.isPairingClaimed()
        _peerName.value = tokenStore.getPeerConnection()?.friendName
    }

    fun connectPhone(code: String) {
        if (_claimingPeer.value) return
        viewModelScope.launch {
            _claimingPeer.value = true
            _peerError.value = null
            peerRelayClient.claimPairingCode(code.trim())
                .onSuccess { connection ->
                    _peerName.value = connection.friendName
                    onPeerChanged()
                }
                .onFailure { error -> _peerError.value = error.message ?: "The phone could not be paired." }
            _claimingPeer.value = false
        }
    }

    fun disconnectPhone() {
        viewModelScope.launch {
            peerRelayClient.disconnect()
            _peerName.value = null
            _peerError.value = null
            onPeerChanged()
        }
    }
}

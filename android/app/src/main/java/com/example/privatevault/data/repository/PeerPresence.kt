package com.example.privatevault.data.repository

data class PeerPresence(
    val isOnline: Boolean = false,
    val lastSeenAtMillis: Long? = null
)

internal fun reducePeerPresence(
    current: PeerPresence,
    isOnline: Boolean,
    observedAtMillis: Long
): PeerPresence {
    require(observedAtMillis > 0L) { "Presence timestamps must be positive." }
    val lastSeenAtMillis = if (isOnline || current.isOnline) {
        observedAtMillis
    } else {
        current.lastSeenAtMillis
    }
    return PeerPresence(isOnline = isOnline, lastSeenAtMillis = lastSeenAtMillis)
}

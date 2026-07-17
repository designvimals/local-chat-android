package com.example.privatevault.data.local

enum class PairingClientType(val wireName: String) {
    Web("web"),
    Android("android");

    companion object {
        fun fromWireName(value: String?): PairingClientType? = entries.firstOrNull { it.wireName == value }
    }
}

data class PairingSlotState(
    val webClaimed: Boolean,
    val androidClaimed: Boolean
) {
    val hasOpenSlot: Boolean
        get() = !webClaimed || !androidClaimed

    val isFullyClaimed: Boolean
        get() = webClaimed && androidClaimed

    fun claim(clientType: PairingClientType): PairingSlotState = when (clientType) {
        PairingClientType.Web -> copy(webClaimed = true)
        PairingClientType.Android -> copy(androidClaimed = true)
    }

    fun claimedClientTypes(): List<String> = buildList {
        if (webClaimed) add(PairingClientType.Web.wireName)
        if (androidClaimed) add(PairingClientType.Android.wireName)
    }

    companion object {
        fun fromLegacyClaimed(claimed: Boolean): PairingSlotState = PairingSlotState(
            webClaimed = claimed,
            androidClaimed = claimed
        )
    }
}

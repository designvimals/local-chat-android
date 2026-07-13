package com.example.privatevault.network

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetwork {
    fun storageEndpoint(configuredEndpoint: String, port: Int = 8080): String {
        val normalized = configuredEndpoint.trim().trimEnd('/')
        if (!normalized.contains("127.0.0.1") && !normalized.contains("localhost")) {
            return normalized
        }

        val lanIp = localIpv4Addresses().firstOrNull()
        return if (lanIp != null) {
            "http://$lanIp:$port"
        } else {
            normalized
        }
    }

    private fun localIpv4Addresses(): List<String> {
        return NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { network -> network.isUp && !network.isLoopback && !network.isVirtual }
            .flatMap { network -> network.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { address -> !address.isLoopbackAddress && !address.isLinkLocalAddress }
            .map { address -> address.hostAddress }
            .filterNotNull()
            .sortedWith(compareByDescending<String> { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") })
            .toList()
    }
}

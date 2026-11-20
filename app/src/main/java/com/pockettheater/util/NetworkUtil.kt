package com.pockettheater.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {
    private val skipPrefixes = listOf("rmnet", "ccmni", "tun", "dummy", "v4-", "lo")

    /** The phone's address on the local Wi-Fi or hotspot network, or null if none. */
    fun localIpv4(): String? {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList()
        } catch (e: Exception) {
            null
        } ?: return null

        val found = interfaces
            .filter { it.isUp && !it.isLoopback && skipPrefixes.none { p -> it.name.startsWith(p) } }
            .flatMap { ni ->
                ni.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { ni.name to (it.hostAddress ?: "") }
            }
            .filter { it.second.isNotEmpty() }

        return (found.firstOrNull { it.first.startsWith("wlan") } ?: found.firstOrNull())?.second
    }
}

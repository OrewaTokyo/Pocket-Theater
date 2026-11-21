package com.pockettheater.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {
    /** One address the phone can be reached on, and what kind of network it belongs to. */
    class Address(val ip: String, val kind: String)

    private val skipPrefixes = listOf("rmnet", "ccmni", "tun", "dummy", "v4-", "lo", "ppp")
    private val hotspotPrefixes = listOf("ap", "swlan", "softap", "wlan1", "wlan2")

    private fun kindOf(name: String): String = when {
        hotspotPrefixes.any { name.startsWith(it) } -> "Hotspot"
        name.startsWith("wlan") -> "Wi-Fi"
        name.startsWith("rndis") || name.startsWith("usb") -> "USB"
        name.startsWith("bt-pan") || name.startsWith("bnep") -> "Bluetooth"
        else -> "Network"
    }

    private fun rank(kind: String): Int = when (kind) {
        "Hotspot" -> 0 // a laptop joined to the phone's hotspot reaches the phone here
        "Wi-Fi" -> 1
        "USB" -> 2
        "Bluetooth" -> 3
        else -> 4
    }

    /** Every local address, best first. When the phone is sharing a hotspot, the hotspot address comes first. */
    fun addresses(): List<Address> {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList()
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return interfaces
            .filter { it.isUp && !it.isLoopback && skipPrefixes.none { p -> it.name.startsWith(p) } }
            .flatMap { ni ->
                ni.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .mapNotNull { a -> a.hostAddress?.let { Address(it, kindOf(ni.name)) } }
            }
            .distinctBy { it.ip }
            .sortedBy { rank(it.kind) }
    }

    /** The phone's best address on the local Wi-Fi or hotspot network, or null if none. */
    fun localIpv4(): String? = addresses().firstOrNull()?.ip
}

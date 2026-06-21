package com.danis.nadi.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {
    fun firstLocalIpv4(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .map { it.hostAddress }
            .firstOrNull()
    }

    fun localOnlyHotspotIpv4(): String? {
        val preferredNames = listOf("wlan1", "ap0", "swlan0", "softap0")
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return preferredNames.asSequence()
            .mapNotNull { preferredName ->
                interfaces.firstOrNull { it.name.equals(preferredName, ignoreCase = true) }
                    ?.firstIpv4Address()
            }
            .firstOrNull()
    }

    private fun NetworkInterface.firstIpv4Address(): String? {
        if (!isUp || isLoopback) return null
        return inetAddresses.toList()
            .asSequence()
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .map { it.hostAddress }
            .firstOrNull()
    }
}

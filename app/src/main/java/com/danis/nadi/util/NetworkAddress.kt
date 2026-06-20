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
}

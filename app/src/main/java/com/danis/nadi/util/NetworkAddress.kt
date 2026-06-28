package com.danis.nadi.util

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {
    fun firstLocalIpv4(): String? {
        return chooseFirstLocalIpv4(ipv4Candidates())
    }

    fun localOnlyHotspotIpv4(): String? {
        return chooseLocalOnlyHotspotIpv4(ipv4Candidates())
    }

    internal fun chooseFirstLocalIpv4(candidates: List<NetworkAddressCandidate>): String? {
        return candidates
            .sortedWith(
                compareBy<NetworkAddressCandidate> { it.interfacePriority() }
                    .thenBy { if (it.address.isPrivateIpv4()) 0 else 1 }
                    .thenBy { it.index }
            )
            .firstOrNull()
            ?.address
    }

    internal fun chooseLocalOnlyHotspotIpv4(candidates: List<NetworkAddressCandidate>): String? {
        return HOTSPOT_INTERFACE_NAMES.asSequence()
            .mapNotNull { preferredName ->
                candidates.firstOrNull { it.interfaceName.equals(preferredName, ignoreCase = true) }?.address
            }
            .firstOrNull()
    }

    private fun ipv4Candidates(): List<NetworkAddressCandidate> {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return interfaces
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMapIndexed { index, networkInterface ->
                networkInterface.inetAddresses.toList()
                    .asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .mapNotNull { address ->
                        val hostAddress = address.hostAddress ?: return@mapNotNull null
                        NetworkAddressCandidate(
                            interfaceName = networkInterface.name,
                            address = hostAddress,
                            index = index
                        )
                    }
            }
            .toList()
    }

    private fun NetworkAddressCandidate.interfacePriority(): Int {
        val name = interfaceName.lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("swlan") -> 0
            name.startsWith("ap") || name.startsWith("softap") -> 1
            name.startsWith("eth") -> 2
            name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("wwan") -> 20
            name.startsWith("pdp_ip") || name.startsWith("tun") || name.startsWith("tap") -> 30
            else -> 10
        }
    }

    private fun String.isPrivateIpv4(): Boolean {
        return startsWith("10.") ||
            startsWith("192.168.") ||
            split(".").let { parts ->
                parts.size == 4 && parts[0] == "172" && (parts[1].toIntOrNull() ?: -1) in 16..31
            }
    }

    private val HOTSPOT_INTERFACE_NAMES = listOf("wlan1", "ap0", "swlan0", "softap0")

    fun detectWifiGatewayIp(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val dhcpInfo = wifiManager.dhcpInfo ?: return null
            val gateway = dhcpInfo.gateway
            if (gateway == 0) return null
            (gateway and 0xFF).toString() + "." +
                    ((gateway shr 8) and 0xFF) + "." +
                    ((gateway shr 16) and 0xFF) + "." +
                    ((gateway shr 24) and 0xFF)
        } catch (e: Exception) {
            null
        }
    }
}

internal data class NetworkAddressCandidate(
    val interfaceName: String,
    val address: String,
    val index: Int
)

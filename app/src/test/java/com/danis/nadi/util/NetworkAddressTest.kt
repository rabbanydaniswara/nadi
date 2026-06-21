package com.danis.nadi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkAddressTest {
    @Test
    fun firstLocalIpv4PrefersWifiBeforeCellularOrVpn() {
        val address = NetworkAddress.chooseFirstLocalIpv4(
            listOf(
                NetworkAddressCandidate("rmnet_data0", "10.44.0.12", 0),
                NetworkAddressCandidate("tun0", "10.8.0.2", 1),
                NetworkAddressCandidate("wlan0", "192.168.1.42", 2)
            )
        )

        assertEquals("192.168.1.42", address)
    }

    @Test
    fun firstLocalIpv4FallsBackToGenericPrivateAddress() {
        val address = NetworkAddress.chooseFirstLocalIpv4(
            listOf(
                NetworkAddressCandidate("unknown0", "203.0.113.10", 0),
                NetworkAddressCandidate("unknown1", "172.16.1.8", 1)
            )
        )

        assertEquals("172.16.1.8", address)
    }

    @Test
    fun localOnlyHotspotIpv4OnlyUsesPreferredHotspotInterfaces() {
        val address = NetworkAddress.chooseLocalOnlyHotspotIpv4(
            listOf(
                NetworkAddressCandidate("wlan0", "192.168.1.42", 0),
                NetworkAddressCandidate("ap0", "192.168.43.1", 1)
            )
        )

        assertEquals("192.168.43.1", address)
    }

    @Test
    fun localOnlyHotspotIpv4DoesNotFallbackToRegularWifi() {
        val address = NetworkAddress.chooseLocalOnlyHotspotIpv4(
            listOf(
                NetworkAddressCandidate("wlan0", "192.168.1.42", 0)
            )
        )

        assertNull(address)
    }
}

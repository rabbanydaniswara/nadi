package com.danis.nadi.network.hotspot

sealed class HotspotState {
    data object Idle : HotspotState()
    data object Starting : HotspotState()
    data class Active(
        val ssid: String?,
        val password: String?
    ) : HotspotState()
    data class Failed(
        val message: String
    ) : HotspotState()
}

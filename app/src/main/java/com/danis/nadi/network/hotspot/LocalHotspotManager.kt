package com.danis.nadi.network.hotspot

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.danis.nadi.util.NetworkAddress

class LocalHotspotManager(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    fun start(onStateChanged: (HotspotState) -> Unit) {
        onStateChanged(HotspotState.Starting)
        
        // Cek apakah interface hotspot (ap0, wlan1, dll) memiliki IP address aktif.
        // Jika ada, berarti user telah menyalakan hotspot pribadi mereka secara manual.
        val hotspotIp = NetworkAddress.localOnlyHotspotIpv4()
        if (hotspotIp != null) {
            onStateChanged(
                HotspotState.Active(
                    ssid = "Hotspot Pribadi HP",
                    password = null
                )
            )
        } else {
            onStateChanged(
                HotspotState.Failed(
                    "Hotspot Pribadi belum aktif. Silakan nyalakan Tethering / Hotspot Pribadi di pengaturan HP Anda terlebih dahulu."
                )
            )
        }
    }

    fun stop() {
        // Reservasi local-only hotspot tidak digunakan lagi demi kestabilan koneksi manual
    }
}

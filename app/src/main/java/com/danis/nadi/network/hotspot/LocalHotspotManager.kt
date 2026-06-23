package com.danis.nadi.network.hotspot

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

class LocalHotspotManager(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start(onStateChanged: (HotspotState) -> Unit) {
        if (reservation != null) {
            onStateChanged(HotspotState.Failed("Ruang lokal sudah aktif. Tutup ruang lalu coba lagi."))
            return
        }
        onStateChanged(HotspotState.Starting)
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    this@LocalHotspotManager.reservation = reservation
                    onStateChanged(reservation.toActiveState())
                }

                override fun onStopped() {
                    this@LocalHotspotManager.reservation = null
                    onStateChanged(HotspotState.Idle)
                }

                override fun onFailed(reason: Int) {
                    this@LocalHotspotManager.reservation = null
                    onStateChanged(HotspotState.Failed(reason.toFailureMessage()))
                }
            }, handler)
        } catch (_: SecurityException) {
            onStateChanged(HotspotState.Failed("Nadi perlu izin lokasi/Wi-Fi untuk membuat ruang lokal."))
        } catch (_: IllegalStateException) {
            onStateChanged(HotspotState.Failed("Hotspot lokal belum bisa dimulai karena Wi-Fi sedang sibuk."))
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
    }

    private fun WifiManager.LocalOnlyHotspotReservation.toActiveState(): HotspotState.Active {
        val config = wifiConfiguration()
        return HotspotState.Active(
            ssid = config?.SSID,
            password = config?.preSharedKey
        )
    }

    @Suppress("DEPRECATION")
    private fun WifiManager.LocalOnlyHotspotReservation.wifiConfiguration(): WifiConfiguration? {
        return wifiConfiguration
    }

    private fun Int.toFailureMessage(): String {
        return when (this) {
            WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL ->
                "Ruang lokal belum bisa dibuat karena channel Wi-Fi tidak tersedia."
            WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC ->
                "Ruang lokal belum bisa dibuat di perangkat ini."
            WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE ->
                "Matikan tethering atau mode Wi-Fi lain, lalu coba lagi."
            WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED ->
                "Perangkat membatasi hotspot lokal. Gunakan mode satu Wi-Fi."
            else ->
                "Ruang lokal belum bisa dibuat. Gunakan mode satu Wi-Fi."
        }
    }
}

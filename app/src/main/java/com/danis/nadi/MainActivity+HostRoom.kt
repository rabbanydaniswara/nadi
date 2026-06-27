package com.danis.nadi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.danis.nadi.model.RoomSession
import com.danis.nadi.network.hotspot.HotspotState
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.room.RoomLifecycleService
import com.danis.nadi.room.RoomStartResult
import com.danis.nadi.ui.compose.Screen
import com.danis.nadi.room.ActiveRoom
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun MainActivity.startLocalRoom(roomName: String, hostName: String, pin: String, mode: NetworkMode) {
    if (mode == NetworkMode.HOTSPOT && !hasHotspotPermissions()) {
        pendingHotspotStart = true
        pendingRoomName = roomName
        pendingHostName = hostName
        pendingRoomPin = pin
        pendingNetworkMode = mode
        hotspotPermissionLauncher.launch(requiredHotspotPermissions())
        return
    }
    startLocalRoomWithMode(roomName, hostName, pin, mode)
}

fun MainActivity.startLocalRoomWithMode(roomName: String, hostName: String, pin: String, mode: NetworkMode) {
    stopDashboardPolling()
    val trimmedPin = pin.trim()
    if (trimmedPin.isNotBlank() && !trimmedPin.isValidRoomPin()) {
        Toast.makeText(this, getString(R.string.room_pin_invalid), Toast.LENGTH_LONG).show()
        return
    }
    val startResult = controller.prepareRoom(
        roomName = roomName,
        hostName = hostName,
        mode = mode,
        pin = trimmedPin
    )
    when (startResult) {
        is RoomStartResult.Failed -> {
            Toast.makeText(this, startResult.message, Toast.LENGTH_LONG).show()
            currentScreenState.value = Screen.Setup
        }
        is RoomStartResult.Prepared -> {
            if (mode == NetworkMode.HOTSPOT) {
                startHotspotThenActivate(startResult.session)
            } else {
                activateRoom(startResult.session, NetworkMode.SAME_WIFI, null, null)
            }
        }
    }
}

fun MainActivity.startHotspotThenActivate(preparingSession: RoomSession) {
    controller.hotspotManager.start { state ->
        when (state) {
            HotspotState.Idle -> Unit
            HotspotState.Starting -> {
                Toast.makeText(this, "Menyiapkan hotspot lokal...", Toast.LENGTH_SHORT).show()
            }
            is HotspotState.Active -> {
                if (state.ssid.isNullOrBlank()) {
                    Toast.makeText(this, getString(R.string.hotspot_ssid_unavailable_toast), Toast.LENGTH_LONG).show()
                }
                dashboardHandler.postDelayed(
                    {
                        activateRoom(
                            preparingSession = preparingSession,
                            mode = NetworkMode.HOTSPOT,
                            hotspotSsid = state.ssid,
                            hotspotPassword = state.password
                        )
                    },
                    HOTSPOT_ADDRESS_SETTLE_DELAY_MS
                )
            }
            is HotspotState.Failed -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                activateRoom(
                    preparingSession = preparingSession,
                    mode = NetworkMode.SAME_WIFI_FALLBACK,
                    hotspotSsid = null,
                    hotspotPassword = null
                )
            }
        }
    }
}

fun MainActivity.activateRoom(
    preparingSession: RoomSession,
    mode: NetworkMode,
    hotspotSsid: String?,
    hotspotPassword: String?
) {
    val activeRoom = controller.activatePreparedRoom(
        preparingSession = preparingSession,
        mode = mode,
        ssid = hotspotSsid,
        password = hotspotPassword
    )
    requestNotificationPermissionIfNeeded()
    RoomLifecycleService.start(this)
    renderActiveRoom(activeRoom)
    startDashboardPolling()
}

fun MainActivity.renderActiveRoom(activeRoom: ActiveRoom) {
    hostViewModel.loadRoomData(activeRoom.session.sessionId)
    currentScreenState.value = Screen.HostDashboard
}

fun MainActivity.stopActiveRoom() {
    stopDashboardPolling()
    lifecycleScope.launch {
        withContext(Dispatchers.IO) {
            controller.stopActiveRoom()
        }
        RoomLifecycleService.stop(this@stopActiveRoom)
        Toast.makeText(this@stopActiveRoom, "Ruang sudah ditutup.", Toast.LENGTH_SHORT).show()
        showHome()
    }
}

fun MainActivity.startDashboardPolling() {
    dashboardPolling = true
    dashboardHandler.removeCallbacks(refreshRunnable)
    dashboardHandler.post(refreshRunnable)
}

fun MainActivity.stopDashboardPolling() {
    dashboardPolling = false
    dashboardHandler.removeCallbacks(refreshRunnable)
}

fun MainActivity.copyDiagnostics() {
    val diagnostics = controller.diagnostics().toDisplayText()
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Nadi diagnostics", diagnostics))
    Toast.makeText(this, "Diagnostics disalin.", Toast.LENGTH_SHORT).show()
}

fun MainActivity.regenerateJoinLink() {
    val activeRoom = controller.regenerateAccessLink()
    if (activeRoom == null) {
        Toast.makeText(this, "Link belum bisa diperbarui.", Toast.LENGTH_SHORT).show()
        return
    }
    renderActiveRoom(activeRoom)
    Toast.makeText(this, "Link baru dibuat. Link lama sudah ditutup.", Toast.LENGTH_LONG).show()
}

fun MainActivity.copyJoinInstructions() {
    val instructions = buildJoinInstructions()
    if (instructions.isBlank()) return
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Instruksi join Nadi", instructions))
    Toast.makeText(this, "Instruksi join disalin.", Toast.LENGTH_SHORT).show()
}

fun MainActivity.buildJoinInstructions(): String {
    val session = controller.roomManager.currentSession() ?: return ""
    val url = session.localUrl.orEmpty()
    return buildString {
        append("Gabung ke room Nadi: ").append(session.roomName).append("\n")
        when (controller.activeNetworkMode) {
            NetworkMode.HOTSPOT -> {
                append("1. Sambungkan perangkat ke hotspot Nadi.\n")
                controller.hotspotSsid?.takeIf { it.isNotBlank() }?.let {
                    append("Wi-Fi: ").append(it).append("\n")
                }
                controller.hotspotPassword?.takeIf { it.isNotBlank() }?.let {
                    append("Password: ").append(it).append("\n")
                }
                append("2. Buka URL atau scan QR room.\n")
            }
            NetworkMode.SAME_WIFI,
            NetworkMode.SAME_WIFI_FALLBACK -> {
                append("1. Pastikan perangkat berada di Wi-Fi yang sama dengan host.\n")
                append("2. Buka URL atau scan QR room.\n")
            }
        }
        append("URL: ").append(url).append("\n")
        session.pin?.takeIf { it.isNotBlank() }?.let {
            append("PIN room: ").append(it).append("\n")
        }
        append("Masukkan NIM dan Nama saat halaman Nadi terbuka.")
    }
}

fun MainActivity.copyJoinUrl() {
    val url = controller.roomManager.currentSession()?.localUrl.orEmpty()
    if (url.isBlank()) return
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Nadi room URL", url))
    Toast.makeText(this, "URL ruang disalin.", Toast.LENGTH_SHORT).show()
}

fun MainActivity.buildWifiQrPayload(ssid: String, password: String): String {
    return "WIFI:T:WPA;S:${ssid.escapeWifiQr()};P:${password.escapeWifiQr()};;"
}

fun String.escapeWifiQr(): String {
    return replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")
}


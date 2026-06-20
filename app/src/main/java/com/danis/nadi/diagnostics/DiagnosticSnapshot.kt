package com.danis.nadi.diagnostics

import com.danis.nadi.room.NetworkMode
import com.danis.nadi.room.RoomLifecycleState

data class DiagnosticSnapshot(
    val lifecycleState: RoomLifecycleState,
    val networkMode: NetworkMode,
    val serverPort: Int?,
    val joinUrl: String?,
    val hotspotSsid: String?,
    val hotspotActive: Boolean,
    val clientCount: Int,
    val sharedFileCount: Int,
    val receivedFileCount: Int,
    val messageCount: Int,
    val localAddress: String?
) {
    fun toDisplayText(): String {
        return buildString {
            appendLine("Status: ${lifecycleState.name}")
            appendLine("Mode: ${networkMode.name}")
            appendLine("Port: ${serverPort ?: "-"}")
            appendLine("Alamat lokal: ${localAddress ?: "-"}")
            appendLine("Hotspot aktif: ${if (hotspotActive) "ya" else "tidak"}")
            appendLine("SSID hotspot: ${hotspotSsid ?: "-"}")
            appendLine("Client: $clientCount")
            appendLine("File dibagikan: $sharedFileCount")
            appendLine("File diterima: $receivedFileCount")
            appendLine("Pesan: $messageCount")
            append("URL: ${joinUrl ?: "-"}")
        }
    }
}

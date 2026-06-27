package com.danis.nadi

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.TransferItem
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.network.server.ServerFileRules
import com.danis.nadi.room.ActiveRoom
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.room.RoomLifecycleState
import com.danis.nadi.util.QrCodeGenerator
import java.util.Date

fun MainActivity.renderActiveRoom(activeRoom: ActiveRoom) {
    val session = activeRoom.session
    val joinUrl = session.localUrl.orEmpty()
    val clientCount = controller.roomManager.snapshot().clients.size
    activeStatusText.text = buildStatusLine(activeRoom.mode, clientCount)
    activeRoomNameText.text = session.roomName
    activeRoomCopyText.text = when (activeRoom.mode) {
        NetworkMode.HOTSPOT -> buildString {
            if (activeRoom.hotspotSsid.isNullOrBlank()) {
                append(getString(R.string.hotspot_ssid_unavailable_note))
            } else {
                append(getString(R.string.hotspot_note))
                append("\nWi-Fi: ").append(activeRoom.hotspotSsid)
            }
            if (!activeRoom.hotspotPassword.isNullOrBlank()) append("\nPassword: ").append(activeRoom.hotspotPassword)
        }
        NetworkMode.SAME_WIFI_FALLBACK -> getString(R.string.hotspot_fallback_note)
        NetworkMode.SAME_WIFI -> getString(R.string.same_wifi_note)
    }
    renderJoinGuide(activeRoom)
    joinUrlText.text = joinUrl
    activeRoomPinText.text = getString(R.string.room_pin_active, session.pin.orEmpty())
    if (joinUrl.isNotBlank()) {
        val qrSize = (220 * resources.displayMetrics.density).toInt()
        qrImage.setImageBitmap(QrCodeGenerator.generate(joinUrl, qrSize))
    } else {
        qrImage.setImageDrawable(null)
    }
    renderWifiQr(activeRoom)
    refreshHostDashboard()
    showActiveRoom()
}

fun MainActivity.renderJoinGuide(activeRoom: ActiveRoom) {
    joinStepOpenText.text = getString(R.string.join_step_open_room)
    joinStepIdentityText.text = getString(R.string.join_step_identity)
    when (activeRoom.mode) {
        NetworkMode.HOTSPOT -> {
            val ssid = activeRoom.hotspotSsid.orEmpty()
            val password = activeRoom.hotspotPassword.orEmpty()
            joinStepNetworkText.text = getString(R.string.join_step_hotspot_network)
            if (ssid.isBlank()) {
                joinGuideSummaryText.text = getString(R.string.join_guide_hotspot_unknown_summary)
                joinNetworkDetailText.text = getString(R.string.join_network_hotspot_unknown_detail)
            } else {
                joinGuideSummaryText.text = getString(R.string.join_guide_hotspot_summary)
                joinNetworkDetailText.text = getString(
                    R.string.join_network_hotspot_detail,
                    "\nWi-Fi: $ssid",
                    password.takeIf { it.isNotBlank() }?.let { "\nPassword: $it" }.orEmpty()
                )
            }
        }
        NetworkMode.SAME_WIFI_FALLBACK -> {
            joinGuideSummaryText.text = getString(R.string.join_guide_same_wifi_summary)
            joinStepNetworkText.text = getString(R.string.join_step_same_wifi_network)
            joinNetworkDetailText.text = getString(R.string.join_network_fallback_detail)
        }
        NetworkMode.SAME_WIFI -> {
            joinGuideSummaryText.text = getString(R.string.join_guide_same_wifi_summary)
            joinStepNetworkText.text = getString(R.string.join_step_same_wifi_network)
            joinNetworkDetailText.text = getString(R.string.join_network_same_wifi_detail)
        }
    }
}

fun MainActivity.renderWifiQr(activeRoom: ActiveRoom) {
    val ssid = activeRoom.hotspotSsid.orEmpty()
    if (activeRoom.mode != NetworkMode.HOTSPOT || ssid.isBlank()) {
        wifiQrTitleText.gone()
        wifiQrImage.gone()
        wifiQrImage.setImageDrawable(null)
        return
    }
    val password = activeRoom.hotspotPassword.orEmpty()
    val qrSize = (190 * resources.displayMetrics.density).toInt()
    wifiQrImage.setImageBitmap(QrCodeGenerator.generate(buildWifiQrPayload(ssid, password), qrSize))
    wifiQrTitleText.visible()
    wifiQrImage.visible()
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

fun MainActivity.buildStatusLine(mode: NetworkMode, clientCount: Int): String {
    val modeLabel = when (mode) {
        NetworkMode.HOTSPOT -> "Hotspot lokal"
        NetworkMode.SAME_WIFI_FALLBACK -> "Satu Wi-Fi fallback"
        NetworkMode.SAME_WIFI -> "Satu Wi-Fi"
    }
    return "Aktif - $modeLabel - $clientCount perangkat"
}

fun MainActivity.refreshHostDashboard() {
    if (controller.lifecycleState == RoomLifecycleState.ACTIVE) {
        controller.cleanupExpiredChatAttachments()
    }
    val snapshot = controller.roomManager.snapshot()
    val shared = controller.roomManager.sharedFiles()
    val received = controller.roomManager.receivedFiles()
    val chatStorage = controller.roomManager.chatAttachmentStorageStats()
    val messages = snapshot.messages
    if (controller.lifecycleState == RoomLifecycleState.ACTIVE) {
        controller.persistRecentTransfers()
        activeStatusText.text = buildStatusLine(controller.activeNetworkMode, snapshot.clients.size)
        diagnosticsText.text = controller.diagnostics().toDisplayText()
    }
    fileRoomSummaryText.text = buildString {
        append(getString(R.string.file_room_summary, shared.size, received.size))
        append("\n")
        append("Lampiran chat: ")
        append(chatStorage.availableCount)
        append(" aktif")
        if (chatStorage.expiredCount > 0) {
            append(", ")
            append(chatStorage.expiredCount)
            append(" kedaluwarsa")
        }
        append(" - ")
        append(FileSizeFormatter.format(chatStorage.totalBytes))
        append(" / ")
        append(FileSizeFormatter.format(ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES))
    }
    fileRoomLocationText.text = controller.currentRoomFolderPath()
        ?.let { getString(R.string.file_room_location, it) }
        ?: getString(R.string.file_room_location_pending)
    participantSummaryText.text = getString(R.string.participants_summary, snapshot.clients.size)
    renderParticipantList(snapshot.clients)
    renderTransferList(sharedFilesList, shared, getString(R.string.shared_files_empty))
    renderTransferList(receivedFilesList, received, getString(R.string.received_files_empty))
    hostChatRenderer.render(messages)
    val recent = controller.roomManager.recentTransfers()
    val history = controller.recentHistory()
    renderActiveHistoryList(recent, history)
    recentEmptyText.text = if (recent.isNotEmpty()) {
        recent.joinToString(separator = "\n") { it.displayLine() }
    } else if (history.isNotEmpty()) {
        history.take(5).joinToString(separator = "\n") { it.displayLine() }
    } else {
        getString(R.string.recent_empty)
    }
}

fun ConnectedClient.identityLine(): String {
    return if (nim.isNotBlank() || name.isNotBlank()) {
        "${nim.ifBlank { "-" }} - ${name.ifBlank { displayName }}"
    } else {
        displayName
    }
}

fun ConnectedClient.deviceLine(): String {
    return "${ipAddress.ifBlank { "-" }} - ${userAgent.shortUserAgent()}"
}

fun MainActivity.renderParticipantList(clients: List<ConnectedClient>) {
    clientListContainer.removeAllViews()
    if (clients.isEmpty()) {
        clientListContainer.addView(simpleStateCard(getString(R.string.participants_empty)))
        return
    }
    clients.forEachIndexed { index, client ->
        clientListContainer.addView(participantCard(client, index > 0))
    }
}

fun MainActivity.participantCard(client: ConnectedClient, hasTopMargin: Boolean): View {
    val activity = this
    val card = baseInfoCard(hasTopMargin)
    val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
    }
    val icon = ImageView(activity).apply {
        setImageResource(R.drawable.ic_participants)
        imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.nadi_green)
        )
        layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
            marginEnd = 10.dp()
        }
    }
    val textColumn = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    textColumn.addView(TextView(activity).apply {
        text = client.identityLine()
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_graphite))
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    })
    textColumn.addView(TextView(activity).apply {
        text = client.deviceLine()
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_soft_ink))
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 2.dp()
        }
    })
    textColumn.addView(TextView(activity).apply {
        text = getString(R.string.participant_status_active)
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_success))
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 2.dp()
        }
    })
    row.addView(icon)
    row.addView(textColumn)
    card.addView(row)
    return card
}

fun MainActivity.renderActiveHistoryList(recent: List<TransferItem>, history: List<TransferHistoryItem>) {
    activeRoomHistoryList.removeAllViews()
    when {
        recent.isNotEmpty() -> recent.forEachIndexed { index, item ->
            activeRoomHistoryList.addView(historyCard(
                fileName = item.fileName,
                meta = listOf(
                    item.direction.label(),
                    FileSizeFormatter.format(item.sizeBytes),
                    item.status.label(item.progress)
                ).joinToString(" - "),
                time = historyTimeFormat.format(Date(item.createdAt)),
                senderName = item.senderName,
                hasTopMargin = index > 0
            ))
        }
        history.isNotEmpty() -> history.take(8).forEachIndexed { index, item ->
            activeRoomHistoryList.addView(historyCard(
                fileName = item.fileName,
                meta = listOf(
                    item.direction.label(),
                    FileSizeFormatter.format(item.sizeBytes),
                    item.status.label(item.progress)
                ).joinToString(" - "),
                time = historyTimeFormat.format(Date(item.createdAt)),
                senderName = item.senderName,
                hasTopMargin = index > 0
            ))
        }
        else -> activeRoomHistoryList.addView(simpleStateCard(getString(R.string.history_empty)))
    }
}

fun MainActivity.historyCard(
    fileName: String,
    meta: String,
    time: String,
    senderName: String?,
    hasTopMargin: Boolean
): View {
    val activity = this
    val card = baseInfoCard(hasTopMargin)
    val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
    }
    val icon = ImageView(activity).apply {
        setImageResource(R.drawable.ic_history)
        imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(activity, R.color.nadi_green)
        )
        layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
            marginEnd = 10.dp()
        }
    }
    val textColumn = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    textColumn.addView(TextView(activity).apply {
        text = fileName
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_graphite))
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    })
    textColumn.addView(TextView(activity).apply {
        text = meta
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_soft_ink))
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 2.dp()
        }
    })
    textColumn.addView(TextView(activity).apply {
        text = listOfNotNull(senderName?.takeIf { it.isNotBlank() }, time).joinToString(" - ")
        setTextColor(ContextCompat.getColor(activity, R.color.nadi_green))
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 2.dp()
        }
    })
    row.addView(icon)
    row.addView(textColumn)
    card.addView(row)
    return card
}

internal fun String.shortUserAgent(): String {
    return trim()
        .ifBlank { "Browser" }
        .replace(Regex("""\s+"""), " ")
        .take(80)
}

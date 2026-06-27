package com.danis.nadi

import com.danis.nadi.file.FileSizeFormatter
import com.danis.nadi.history.TransferHistoryItem
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus

fun MainActivity.refreshHistoryScreen() {
    val history = controller.recentHistory()
    historyListText.text = if (history.isEmpty()) {
        getString(R.string.history_empty)
    } else {
        history.joinToString(separator = "\n\n") { it.displayLine() }
    }
}

fun TransferItem.displayLine(): String {
    return "${direction.label()} - ${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.label(progress)}"
}

fun TransferHistoryItem.displayLine(): String {
    return "${direction.label()} - ${fileName}\n${FileSizeFormatter.format(sizeBytes)} - ${status.label(progress)} - tersimpan lokal"
}

fun TransferDirection.label(): String {
    return when (this) {
        TransferDirection.SHARED -> "Dibagikan"
        TransferDirection.UPLOAD -> "Diterima"
        TransferDirection.DOWNLOAD -> "Diunduh"
        TransferDirection.CHAT_ATTACHMENT -> "Lampiran chat"
    }
}

fun TransferStatus.label(progress: Int): String {
    return when (this) {
        TransferStatus.PENDING -> "Menunggu"
        TransferStatus.RUNNING -> "Berjalan $progress%"
        TransferStatus.SUCCESS -> "Tersedia"
        TransferStatus.DOWNLOADED -> "Diunduh"
        TransferStatus.EXPIRED -> "Kedaluwarsa"
        TransferStatus.FAILED -> "Gagal"
    }
}

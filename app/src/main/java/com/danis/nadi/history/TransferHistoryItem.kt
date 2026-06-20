package com.danis.nadi.history

import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus

data class TransferHistoryItem(
    val transferId: String,
    val fileName: String,
    val sizeBytes: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Int,
    val createdAt: Long,
    val senderName: String?
)

fun TransferItem.toHistoryItem(): TransferHistoryItem {
    return TransferHistoryItem(
        transferId = transferId,
        fileName = fileName,
        sizeBytes = sizeBytes,
        direction = direction,
        status = status,
        progress = progress,
        createdAt = createdAt,
        senderName = senderName
    )
}

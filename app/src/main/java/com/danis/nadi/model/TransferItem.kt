package com.danis.nadi.model

data class TransferItem(
    val transferId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Int,
    val createdAt: Long,
    val localUri: String?,
    val senderName: String?
)

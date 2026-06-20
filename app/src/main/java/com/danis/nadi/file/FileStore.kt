package com.danis.nadi.file

import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferDirection
import java.io.InputStream

interface FileStore {
    fun openForDownload(transfer: TransferItem): FilePayload?

    fun saveUpload(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream
    ): TransferItem

    fun saveRoomFile(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream,
        roomId: String?,
        folderName: String,
        direction: TransferDirection,
        senderName: String?
    ): TransferItem {
        return saveUpload(fileName, mimeType, inputStream).copy(
            direction = direction,
            senderName = senderName
        )
    }
}

data class FilePayload(
    val inputStream: InputStream,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

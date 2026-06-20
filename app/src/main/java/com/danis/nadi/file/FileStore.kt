package com.danis.nadi.file

import com.danis.nadi.model.TransferItem
import java.io.InputStream

interface FileStore {
    fun openForDownload(transfer: TransferItem): FilePayload?

    fun saveUpload(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream
    ): TransferItem
}

data class FilePayload(
    val inputStream: InputStream,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

package com.danis.nadi.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.security.TokenGenerator
import java.io.File
import java.io.InputStream

class AndroidFileStore(
    private val context: Context,
    private val tokenGenerator: TokenGenerator = TokenGenerator(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) : FileStore {

    fun createSharedTransfer(uri: Uri): TransferItem {
        val metadata = queryMetadata(uri)
        return TransferItem(
            transferId = tokenGenerator.newSessionId(16),
            fileName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            direction = TransferDirection.SHARED,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = clock(),
            localUri = uri.toString(),
            senderName = "Host"
        )
    }

    override fun openForDownload(transfer: TransferItem): FilePayload? {
        val localUri = transfer.localUri ?: return null
        val input = if (localUri.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(localUri))
        } else {
            File(localUri).takeIf { it.exists() }?.inputStream()
        } ?: return null

        return FilePayload(
            inputStream = input,
            fileName = transfer.fileName,
            mimeType = transfer.mimeType ?: "application/octet-stream",
            sizeBytes = transfer.sizeBytes
        )
    }

    override fun saveUpload(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream
    ): TransferItem {
        val receivedDir = File(context.getExternalFilesDir(null), "received")
        if (!receivedDir.exists()) {
            receivedDir.mkdirs()
        }
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "upload.bin" }
        val targetName = FileNameResolver.uniqueName(safeName) { File(receivedDir, it).exists() }
        val targetFile = File(receivedDir, targetName)
        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return TransferItem(
            transferId = tokenGenerator.newSessionId(16),
            fileName = targetName,
            mimeType = mimeType ?: "application/octet-stream",
            sizeBytes = targetFile.length(),
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = clock(),
            localUri = targetFile.absolutePath,
            senderName = "Browser"
        )
    }

    private fun queryMetadata(uri: Uri): FileMetadata {
        var displayName = "File Nadi"
        var sizeBytes = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return FileMetadata(displayName, mimeType, sizeBytes)
    }
}

private data class FileMetadata(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long
)

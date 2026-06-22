package com.danis.nadi.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fileRoomTreeUriProvider: () -> String? = { null }
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

    override fun deleteStoredFile(transfer: TransferItem): Boolean {
        val localUri = transfer.localUri ?: return false
        return runCatching {
            if (localUri.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(localUri), null, null) > 0
            } else {
                File(localUri).delete()
            }
        }.getOrDefault(false)
    }

    override fun saveUpload(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream
    ): TransferItem {
        return saveRoomFile(
            fileName = fileName,
            mimeType = mimeType,
            inputStream = inputStream,
            roomId = null,
            folderName = "received",
            direction = TransferDirection.UPLOAD,
            senderName = "Browser"
        )
    }

    override fun saveRoomFile(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream,
        roomId: String?,
        folderName: String,
        direction: TransferDirection,
        senderName: String?
    ): TransferItem {
        val publicFolder = when {
            direction == TransferDirection.UPLOAD && folderName == RECEIVED_FOLDER -> folderName
            direction == TransferDirection.CHAT_ATTACHMENT && folderName == CHAT_DOWNLOADS_FOLDER -> folderName
            else -> null
        }
        if (publicFolder != null) {
            return savePublicRoomFile(fileName, mimeType, inputStream, roomId, publicFolder, direction, senderName)
        }
        return saveAppRoomFile(fileName, mimeType, inputStream, roomId, folderName, direction, senderName)
    }

    fun roomFolder(roomId: String?, folderName: String): File = publicRoomDirectory(roomId, folderName)

    fun roomFolderLabel(roomId: String?, folderName: String): String {
        return if (!fileRoomTreeUriProvider().isNullOrBlank() && folderName == RECEIVED_FOLDER) {
            "Folder pilihan/$PUBLIC_ROOT_FOLDER/${roomId.safePathSegment()}/${folderName.safePathSegment()}"
        } else {
            roomFolder(roomId, folderName).absolutePath
        }
    }

    private fun savePublicRoomFile(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream,
        roomId: String?,
        folderName: String,
        direction: TransferDirection,
        senderName: String?
    ): TransferItem {
        fileRoomTreeUriProvider()?.takeIf { it.isNotBlank() }?.let { treeUri ->
            saveTreeRoomFile(fileName, mimeType, inputStream, roomId, folderName, direction, senderName, Uri.parse(treeUri))?.let {
                return it
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return saveAppRoomFile(fileName, mimeType, inputStream, roomId, folderName, direction, senderName)
        }
        val safeName = fileName.safeFileName()
        val relativePath = publicRelativePath(roomId, folderName)
        val targetName = FileNameResolver.uniqueName(safeName) { mediaFileExists(relativePath, it) }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values)
            ?: return saveAppRoomFile(fileName, mimeType, inputStream, roomId, folderName, direction, senderName)

        var bytesWritten = 0L
        context.contentResolver.openOutputStream(uri)?.use { output ->
            inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    bytesWritten += read
                }
            }
        } ?: return saveAppRoomFile(fileName, mimeType, inputStream, roomId, folderName, direction, senderName)

        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )

        return TransferItem(
            transferId = tokenGenerator.newSessionId(16),
            fileName = targetName,
            mimeType = mimeType ?: "application/octet-stream",
            sizeBytes = bytesWritten,
            direction = direction,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = clock(),
            localUri = uri.toString(),
            senderName = senderName ?: "Browser"
        )
    }

    private fun saveTreeRoomFile(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream,
        roomId: String?,
        folderName: String,
        direction: TransferDirection,
        senderName: String?,
        treeUri: Uri
    ): TransferItem? = runCatching {
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val nadiDirectory = findOrCreateDirectory(treeUri, rootDocument, PUBLIC_ROOT_FOLDER) ?: return null
        val roomDirectory = findOrCreateDirectory(treeUri, nadiDirectory, roomId.safePathSegment()) ?: return null
        val receivedDirectory = findOrCreateDirectory(treeUri, roomDirectory, folderName.safePathSegment()) ?: return null
        val safeName = fileName.safeFileName()
        val targetName = FileNameResolver.uniqueName(safeName) { documentExists(treeUri, receivedDirectory, it) }
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            receivedDirectory,
            mimeType ?: "application/octet-stream",
            targetName
        ) ?: return null

        var bytesWritten = 0L
        context.contentResolver.openOutputStream(documentUri)?.use { output ->
            inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    bytesWritten += read
                }
            }
        } ?: return null

        TransferItem(
            transferId = tokenGenerator.newSessionId(16),
            fileName = targetName,
            mimeType = mimeType ?: "application/octet-stream",
            sizeBytes = bytesWritten,
            direction = direction,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = clock(),
            localUri = documentUri.toString(),
            senderName = senderName ?: "Browser"
        )
    }.getOrNull()

    private fun saveAppRoomFile(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream,
        roomId: String?,
        folderName: String,
        direction: TransferDirection,
        senderName: String?
    ): TransferItem {
        val targetDir = roomDirectory(roomId, folderName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val safeName = fileName.safeFileName()
        val targetName = FileNameResolver.uniqueName(safeName) { File(targetDir, it).exists() }
        val targetFile = File(targetDir, targetName)
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
            direction = direction,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = clock(),
            localUri = targetFile.absolutePath,
            senderName = senderName ?: "Browser"
        )
    }

    private fun mediaFileExists(relativePath: String, fileName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val args = arrayOf(relativePath, fileName)
        return context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    private fun findOrCreateDirectory(treeUri: Uri, parentDocumentUri: Uri, name: String): Uri? {
        findChildDocument(treeUri, parentDocumentUri, name, DocumentsContract.Document.MIME_TYPE_DIR)?.let {
            return it
        }
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentDocumentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        )
    }

    private fun documentExists(treeUri: Uri, parentDocumentUri: Uri, name: String): Boolean {
        return findChildDocument(treeUri, parentDocumentUri, name, expectedMimeType = null) != null
    }

    private fun findChildDocument(
        treeUri: Uri,
        parentDocumentUri: Uri,
        name: String,
        expectedMimeType: String?
    ): Uri? {
        val parentDocumentId = DocumentsContract.getDocumentId(parentDocumentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)
                val mimeType = cursor.getString(mimeIndex)
                if (displayName == name && (expectedMimeType == null || mimeType == expectedMimeType)) {
                    return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                }
            }
            null
        }
    }

    private fun publicRoomDirectory(roomId: String?, folderName: String): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nadi/${roomId.safePathSegment()}/${folderName.safePathSegment()}")
    }

    private fun publicRelativePath(roomId: String?, folderName: String): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/Nadi/${roomId.safePathSegment()}/${folderName.safePathSegment()}/"
    }

    private fun roomDirectory(roomId: String?, folderName: String): File {
        val safeRoomId = roomId.safePathSegment()
        val safeFolder = folderName.safePathSegment()
        return File(context.getExternalFilesDir(null), "rooms/$safeRoomId/$safeFolder")
    }

    private fun String?.safePathSegment(): String {
        return orEmpty()
            .replace(Regex("""[^A-Za-z0-9_-]"""), "_")
            .ifBlank { "legacy" }
    }

    private fun String.safeFileName(): String {
        return replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "upload.bin" }
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

private const val PUBLIC_ROOT_FOLDER = "Nadi"
private const val RECEIVED_FOLDER = "received"
private const val CHAT_DOWNLOADS_FOLDER = "chat-attachments"

private data class FileMetadata(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long
)

package com.danis.nadi

import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.danis.nadi.model.TransferItem
import com.danis.nadi.network.server.ServerFileRules
import java.io.File

// Package private constants, class and helper extensions
internal data class SelectedFile(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

internal const val HOTSPOT_ADDRESS_SETTLE_DELAY_MS = 1500L
internal const val OPENINTENTS_EXTRA_ABSOLUTE_PATH = "org.openintents.extra.ABSOLUTE_PATH"
internal const val MIUI_FILE_MANAGER_OPEN_ACTION = "miui.intent.action.OPEN"
internal const val MIUI_FILE_MANAGER_HOME_ACTION = "com.android.fileexplorer.export.VIEW_HOME"
internal const val MIUI_EXPLORER_PATH_EXTRA = "explorer_path"
internal const val SAMSUNG_MY_FILES_PACKAGE = "com.sec.android.app.myfiles"
internal const val SAMSUNG_MY_FILES_ACTION = "samsung.myfiles.intent.action.LAUNCH_MY_FILES"
internal const val SAMSUNG_START_PATH_EXTRA = "samsung.myfiles.intent.extra.START_PATH"
internal const val GENERIC_PATH_EXTRA = "path"
internal const val GENERIC_FOLDER_PATH_EXTRA = "folder_path"
internal const val GENERIC_CURRENT_DIRECTORY_EXTRA = "current_directory"

internal val MIUI_FILE_MANAGER_PACKAGES = listOf(
    "com.mi.android.globalFileexplorer",
    "com.android.fileexplorer"
)

internal val GENERAL_FILE_MANAGER_PACKAGES = listOf(
    "com.google.android.apps.nbu.files",
    "com.oplus.filemanager",
    "com.coloros.filemanager",
    "com.vivo.filemanager",
    "com.huawei.hidisk",
    "com.honor.filemanager",
    "com.asus.filemanager",
    "com.lenovo.FileBrowser2",
    "me.zhanghai.android.files"
)

internal val DOCUMENTS_UI_TARGETS = listOf(
    ComponentName("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity"),
    ComponentName("com.android.documentsui", "com.android.documentsui.files.FilesActivity")
)

fun MainActivity.openFilePicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    filePicker.launch(intent)
}

fun MainActivity.openHostChatAttachmentPicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf(
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
                "application/pdf",
                "text/plain",
                "application/zip",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    hostChatAttachmentPicker.launch(intent)
}

fun MainActivity.persistReadPermissionIfPossible(uri: Uri, data: Intent?) {
    val flags = data?.flags ?: return
    val takeFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
    if (takeFlags != 0) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

fun MainActivity.persistTreePermissionIfPossible(uri: Uri, data: Intent?) {
    val flags = data?.flags ?: return
    val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    if (takeFlags != 0) {
        runCatching {
            if (takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (takeFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
    }
}

internal fun MainActivity.querySelectedFile(uri: Uri): SelectedFile {
    var displayName = "lampiran-nadi"
    var sizeBytes = -1L
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) {
                displayName = cursor.getString(nameIndex)?.ifBlank { displayName } ?: displayName
            }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                sizeBytes = cursor.getLong(sizeIndex)
            }
        }
    }
    return SelectedFile(
        fileName = displayName,
        mimeType = contentResolver.getType(uri) ?: displayName.inferredMimeType(),
        sizeBytes = sizeBytes
    )
}

fun String.inferredMimeType(): String {
    return when (substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}

fun MainActivity.openFileRoomFolderPicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    fileRoomFolderPicker.launch(intent)
}



fun MainActivity.openChatAttachment(attachment: TransferItem) {
    val uri = openableUri(attachment) ?: run {
        Toast.makeText(this, "File lampiran belum tersedia.", Toast.LENGTH_SHORT).show()
        return
    }
    val opened = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType ?: attachment.fileName.inferredMimeType())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(intent, "Buka lampiran"))
    }.isSuccess
    if (!opened) {
        Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini.", Toast.LENGTH_LONG).show()
    }
}

fun TransferItem.previewUri(): Uri? {
    val value = localUri?.takeIf { it.isNotBlank() } ?: return null
    return if (value.startsWith("content://") || value.startsWith("file://")) {
        Uri.parse(value)
    } else {
        Uri.fromFile(File(value))
    }
}

fun MainActivity.selectClientUploadFile() {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    clientFilePicker.launch(Intent.createChooser(intent, "Pilih file untuk dikirim"))
}

fun MainActivity.selectClientChatAttachment() {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    clientChatAttachmentPicker.launch(Intent.createChooser(intent, "Pilih lampiran chat"))
}

fun MainActivity.copyUriToTempFile(uri: Uri): File? {
    return try {
        val (name, _) = getUriMetadata(uri)
        val tempFile = File(cacheDir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun MainActivity.getUriMetadata(uri: Uri): Pair<String, Long> {
    var name = "file"
    var size = 0L
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex != -1) name = cursor.getString(nameIndex)
            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
        }
    }
    return Pair(name, size)
}

fun MainActivity.openFileRoomLocation() {
    val path = controller.currentRoomFolderPath() ?: return
    val uri = controller.currentRoomFolderUri()
    openFolderLocation(path, uri)
}

fun MainActivity.openChatAttachmentsLocation() {
    val path = controller.currentRoomFolderPath(ServerFileRules.CHAT_DOWNLOADS_FOLDER) ?: return
    val uri = controller.currentRoomFolderUri(ServerFileRules.CHAT_DOWNLOADS_FOLDER)
    openFolderLocation(path, uri)
}

fun MainActivity.clearChatAttachments() {
    val cleared = controller.clearChatAttachments()
    refreshHostDashboard()
    val message = if (cleared > 0) {
        resources.getQuantityString(R.plurals.chat_attachments_cleared, cleared, cleared)
    } else {
        getString(R.string.chat_attachments_empty)
    }
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun MainActivity.openFolderLocation(path: String, folderUri: Uri?) {
    if (!path.startsWith("/") && folderUri == null) {
        Toast.makeText(this, getString(R.string.folder_open_unsupported), Toast.LENGTH_LONG).show()
        return
    }
    val folder = path.takeIf { it.startsWith("/") }?.let(::File)
    if (folder != null && !folder.exists()) {
        folder.mkdirs()
    }
    val opened = folderOpenIntents(folder, folderUri ?: folder?.externalStorageDocumentUri()).any { intent ->
        runCatching {
            startActivity(intent)
        }.isSuccess
    }
    if (!opened) {
        Toast.makeText(this, getString(R.string.folder_open_unsupported), Toast.LENGTH_LONG).show()
    }
}

fun MainActivity.folderOpenIntents(folder: File?, folderUri: Uri?): List<Intent> {
    val documentUri = folderUri ?: folder?.externalStorageDocumentUri()
    val contentUri = folder?.let {
        runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
        }.getOrNull()
    }
    val intents = mutableListOf<Intent>()
    intents.addAll(samsungFileManagerIntents(folder, documentUri))
    intents.addAll(miuiFileManagerIntents(folder, documentUri, contentUri))
    intents.addAll(discoveredDirectoryHandlerIntents(folder, documentUri))
    intents.addAll(knownFileManagerIntents(folder, documentUri, contentUri))
    intents.addAll(documentsUiFolderIntents(folder, documentUri))
    return intents.filter { it.resolveActivity(packageManager) != null }
}

fun MainActivity.samsungFileManagerIntents(folder: File?, folderUri: Uri?): List<Intent> {
    val path = folder?.absolutePath ?: return emptyList()
    return listOf(
        Intent(SAMSUNG_MY_FILES_ACTION).apply {
            setPackage(SAMSUNG_MY_FILES_PACKAGE)
            putFolderPathExtras(path)
            folderUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        Intent(Intent.ACTION_VIEW).apply {
            setPackage(SAMSUNG_MY_FILES_PACKAGE)
            folderUri?.let { setDataAndType(it, DocumentsContract.Document.MIME_TYPE_DIR) }
            putFolderPathExtras(path)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
}

fun MainActivity.miuiFileManagerIntents(folder: File?, documentUri: Uri?, contentUri: Uri?): List<Intent> {
    val path = folder?.absolutePath ?: return emptyList()
    return MIUI_FILE_MANAGER_PACKAGES.flatMap { targetPackage ->
        listOf(
            Intent(MIUI_FILE_MANAGER_OPEN_ACTION).apply {
                setPackage(targetPackage)
                putFolderPathExtras(path)
                documentUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            Intent(MIUI_FILE_MANAGER_HOME_ACTION).apply {
                setPackage(targetPackage)
                putFolderPathExtras(path)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            Intent(MIUI_FILE_MANAGER_OPEN_ACTION).apply {
                setPackage(targetPackage)
                (documentUri ?: contentUri)?.let { data = it }
                putFolderPathExtras(path)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        )
    }
}

fun MainActivity.discoveredDirectoryHandlerIntents(folder: File?, folderUri: Uri?): List<Intent> {
    val uri = folderUri ?: return emptyList()
    val baseIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    return packageManager.queryIntentActivities(baseIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .mapNotNull { resolved ->
            val activityInfo = resolved.activityInfo ?: return@mapNotNull null
            ComponentName(activityInfo.packageName, activityInfo.name)
        }
        .distinct()
        .map { target ->
            Intent(baseIntent).apply {
                component = target
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                folder?.let { putFolderPathExtras(it.absolutePath) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
}

fun MainActivity.knownFileManagerIntents(folder: File?, documentUri: Uri?, contentUri: Uri?): List<Intent> {
    val path = folder?.absolutePath
    val folderDataUris = listOfNotNull(documentUri, contentUri).distinct()
    return GENERAL_FILE_MANAGER_PACKAGES.flatMap { targetPackage ->
        folderDataUris.flatMap { uri ->
            listOf(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage(targetPackage)
                    setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                    path?.let { putFolderPathExtras(it) }
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                },
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage(targetPackage)
                    data = uri
                    path?.let { putFolderPathExtras(it) }
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            )
        }
    }
}

fun MainActivity.documentsUiFolderIntents(folder: File?, folderUri: Uri?): List<Intent> {
    val intents = mutableListOf<Intent>()
    folderUri?.let { uri ->
        DOCUMENTS_UI_TARGETS.forEach { target ->
            intents.add(
                Intent(Intent.ACTION_VIEW).apply {
                    component = target
                    setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    folder?.let { putFolderPathExtras(it.absolutePath) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            )
            intents.add(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage(target.packageName)
                    setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    folder?.let { putFolderPathExtras(it.absolutePath) }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            )
        }
    }
    return intents
}

fun Intent.putFolderPathExtras(path: String) {
    putExtra(OPENINTENTS_EXTRA_ABSOLUTE_PATH, path)
    putExtra(MIUI_EXPLORER_PATH_EXTRA, path)
    putExtra(SAMSUNG_START_PATH_EXTRA, path)
    putExtra(GENERIC_PATH_EXTRA, path)
    putExtra(GENERIC_FOLDER_PATH_EXTRA, path)
    putExtra(GENERIC_CURRENT_DIRECTORY_EXTRA, path)
}

fun File.externalStorageDocumentUri(): Uri? {
    val rootPath = Environment.getExternalStorageDirectory().absolutePath.trimEnd(File.separatorChar)
    val folderPath = absolutePath.trimEnd(File.separatorChar)
    if (!folderPath.startsWith(rootPath)) return null
    val relativePath = folderPath
        .removePrefix(rootPath)
        .trimStart(File.separatorChar)
        .replace(File.separatorChar, '/')
    val documentId = if (relativePath.isBlank()) {
        "primary:"
    } else {
        "primary:$relativePath"
    }
    return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
}

fun MainActivity.openableUri(attachment: TransferItem): Uri? {
    val value = attachment.localUri?.takeIf { it.isNotBlank() } ?: return null
    return when {
        value.startsWith("content://") || value.startsWith("file://") -> Uri.parse(value)
        else -> {
            val file = File(value).takeIf { it.exists() } ?: return null
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        }
    }
}

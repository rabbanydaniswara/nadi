package com.danis.nadi

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.network.server.ServerFileRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun MainActivity.openClientChatAttachment(attachment: TransferItem) {
    val localFile = File(attachment.localUri ?: "")
    if (attachment.localUri != null && localFile.exists()) {
        openChatAttachment(attachment)
    } else {
        downloadClientAttachment(attachment)
    }
}

fun MainActivity.ensureClientAttachmentTransfer(message: ChatMessage) {
    val transferId = message.attachmentTransferId ?: return
    if (clientTransfersMap.containsKey(transferId)) return

    val publicFolder = controller.fileStore.roomFolder(null, ServerFileRules.CHAT_DOWNLOADS_FOLDER)
    val localFile = File(publicFolder, message.attachmentFileName ?: "")
    val exists = localFile.exists()

    val transfer = TransferItem(
        transferId = transferId,
        fileName = message.attachmentFileName ?: "file",
        mimeType = null,
        sizeBytes = -1L,
        direction = TransferDirection.CHAT_ATTACHMENT,
        status = if (exists) TransferStatus.SUCCESS else if (message.attachmentStatus == "expired") TransferStatus.EXPIRED else TransferStatus.PENDING,
        progress = if (exists) 100 else 0,
        createdAt = message.createdAt,
        localUri = if (exists) localFile.absolutePath else null,
        senderName = message.senderName
    )
    clientTransfersMap[transferId] = transfer
}

fun MainActivity.downloadClientAttachment(attachment: TransferItem) {
    val client = roomClient ?: return
    Toast.makeText(this, "Mengunduh ${attachment.fileName}...", Toast.LENGTH_SHORT).show()
    val tempDir = File(cacheDir, "downloads")
    // onFinished callback dari RoomClient dijalankan di main thread (via runOnMain).
    // Kita launch IO coroutine di sini untuk memindahkan saveRoomFile ke background.
    client.downloadFile(attachment.transferId, attachment.fileName, tempDir) { success, tempFile ->
        if (success && tempFile != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val saved = tempFile.inputStream().use { input ->
                        controller.fileStore.saveRoomFile(
                            fileName = attachment.fileName,
                            mimeType = attachment.mimeType,
                            inputStream = input,
                            roomId = null,
                            folderName = ServerFileRules.CHAT_DOWNLOADS_FOLDER,
                            direction = TransferDirection.CHAT_ATTACHMENT,
                            senderName = attachment.senderName
                        )
                    }
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        clientTransfersMap[attachment.transferId] = saved
                        Toast.makeText(this@downloadClientAttachment, "Download selesai: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
                        fetchLatestClientChat()
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@downloadClientAttachment, "Gagal menyimpan file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Gagal mengunduh file.", Toast.LENGTH_SHORT).show()
        }
    }
}

fun MainActivity.sendClientChatMessage(text: String) {
    val client = roomClient ?: return
    val attachmentUri = clientPendingAttachmentUri.value

    if (text.isEmpty() && attachmentUri == null) return

    if (attachmentUri != null) {
        // copyUriToTempFile melakukan ContentResolver I/O — dipindahkan ke Dispatchers.IO
        lifecycleScope.launch(Dispatchers.IO) {
            val tempFile = copyUriToTempFile(attachmentUri)
            if (tempFile == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@sendClientChatMessage, "Gagal memproses lampiran.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@sendClientChatMessage, "Mengirim lampiran...", Toast.LENGTH_SHORT).show()
                client.uploadFile(tempFile, isAttachment = true, text = text, onProgress = { _ ->
                    // progress tidak ditampilkan saat ini
                }, onFinished = { success, _ ->
                    tempFile.delete()
                    if (success) {
                        clientPendingAttachmentUri.value = null
                        Toast.makeText(this@sendClientChatMessage, "Pesan lampiran terkirim.", Toast.LENGTH_SHORT).show()
                        fetchLatestClientChat()
                    } else {
                        Toast.makeText(this@sendClientChatMessage, "Gagal mengirim lampiran.", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    } else {
        val prefs = getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE)
        val senderId = prefs.getString("client_id", "").orEmpty()
        val senderName = prefs.getString("client_name", "").orEmpty()
        val optimisticMessage = ChatMessage(
            messageId = "opt_${System.currentTimeMillis()}_${(0..9999).random()}",
            senderId = senderId,
            senderName = senderName,
            text = text,
            createdAt = System.currentTimeMillis(),
            status = "sent"
        )
        clientViewModel.addMessage(optimisticMessage)

        client.sendChatMessage(text) { success ->
            // Hapus optimistic message agar tidak bertumpuk menjadi double di UI
            lifecycleScope.launch(Dispatchers.IO) {
                database.chatMessageDao().deleteMessageById(optimisticMessage.messageId)
            }
            if (success) {
                fetchLatestClientChat()
            } else {
                Toast.makeText(this, "Gagal mengirim pesan.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun MainActivity.addRealMessage(msg: ChatMessage) {
    clientViewModel.addMessage(msg)
    ensureClientAttachmentTransfer(msg)
}

fun MainActivity.fetchLatestClientChat() {
    val client = roomClient ?: return
    val lastTimestamp = clientViewModel.chatMessages.value
        .filter { !it.messageId.startsWith("opt_") }
        .maxByOrNull { it.createdAt }?.createdAt ?: 0L
    client.fetchChatHistory(after = lastTimestamp) { messages ->
        if (messages.isNotEmpty()) {
            clientViewModel.addMessages(messages)
            messages.forEach { ensureClientAttachmentTransfer(it) }
        }
    }
}

fun MainActivity.handleClientChatAttachmentSelected(uri: Uri) {
    clientPendingAttachmentUri.value = uri
    val (name, _) = getUriMetadata(uri)
    Toast.makeText(this, "Lampiran dipilih: $name", Toast.LENGTH_SHORT).show()
}

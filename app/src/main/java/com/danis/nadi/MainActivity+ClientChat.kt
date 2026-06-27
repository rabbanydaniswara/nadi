package com.danis.nadi

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.network.server.ServerFileRules
import com.danis.nadi.ui.HostChatRenderer
import java.io.File

fun MainActivity.setupClientChatRenderer() {
    clientChatRenderer = HostChatRenderer(
        context = this,
        scrollView = clientChatScrollView,
        container = clientChatMessagesContainer,
        hostIdProvider = { getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE).getString("client_id", "").orEmpty() },
        hostNameProvider = { getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE).getString("client_name", "").orEmpty() },
        roomIdProvider = { roomClient?.token ?: roomClient?.pin ?: "client_room" },
        attachmentProvider = { transferId -> clientTransfersMap[transferId] },
        onPreviewImage = ::showImageAttachmentPreview,
        onOpenAttachment = ::openClientChatAttachment
    )
}

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
    client.downloadFile(attachment.transferId, attachment.fileName, tempDir) { success, tempFile ->
        if (success && tempFile != null) {
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
                clientTransfersMap[attachment.transferId] = saved
                clientChatRenderer.render(clientChatMessages)
                Toast.makeText(this, "Download selesai: ${attachment.fileName}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal menyimpan file.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Gagal mengunduh file.", Toast.LENGTH_SHORT).show()
        }
    }
}

fun MainActivity.sendClientChatMessage() {
    val client = roomClient ?: return
    val text = clientChatInput.text.toString().trim()
    val attachmentUri = clientPendingAttachmentUri

    if (text.isEmpty() && attachmentUri == null) return

    clientChatInput.isEnabled = false
    clientSendChatButton.isEnabled = false
    clientAttachButton.isEnabled = false

    if (attachmentUri != null) {
        val tempFile = copyUriToTempFile(attachmentUri)
        if (tempFile == null) {
            Toast.makeText(this, "Gagal memproses lampiran.", Toast.LENGTH_SHORT).show()
            clientChatInput.isEnabled = true
            clientSendChatButton.isEnabled = true
            clientAttachButton.isEnabled = true
            return
        }

        clientAttachmentStatus.text = "Mengirim lampiran..."
        client.uploadFile(tempFile, isAttachment = true, text = text, onProgress = { progress ->
            clientAttachmentStatus.text = "Mengirim lampiran: $progress%"
        }, onFinished = { success, _ ->
            tempFile.delete()
            clientChatInput.isEnabled = true
            clientSendChatButton.isEnabled = true
            clientAttachButton.isEnabled = true
            clientAttachmentStatus.text = ""
            if (success) {
                clientChatInput.setText("")
                clientPendingAttachmentUri = null
                Toast.makeText(this, "Pesan lampiran terkirim.", Toast.LENGTH_SHORT).show()
                fetchLatestClientChat()
            } else {
                Toast.makeText(this, "Gagal mengirim lampiran.", Toast.LENGTH_SHORT).show()
            }
        })
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
        clientChatMessages.add(optimisticMessage)
        clientChatMessages.sortBy { it.createdAt }
        clientChatRenderer.render(clientChatMessages)
        clientChatInput.setText("")
        clientChatScrollView.post {
            clientChatScrollView.fullScroll(View.FOCUS_DOWN)
        }

        client.sendChatMessage(text) { success ->
            clientChatInput.isEnabled = true
            clientSendChatButton.isEnabled = true
            clientAttachButton.isEnabled = true
            if (success) {
                fetchLatestClientChat()
            } else {
                clientChatMessages.removeAll { it.messageId == optimisticMessage.messageId }
                clientChatRenderer.render(clientChatMessages)
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
    val lastTimestamp = clientChatMessages
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
    clientPendingAttachmentUri = uri
    val (name, _) = getUriMetadata(uri)
    clientAttachmentStatus.text = "Lampiran: $name"
}

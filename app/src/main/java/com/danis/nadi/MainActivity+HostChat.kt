package com.danis.nadi

import android.net.Uri
import android.widget.Toast
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.network.server.ServerFileRules

fun MainActivity.sendHostChatAttachment(uri: Uri) {
    val session = controller.roomManager.currentSession()
    if (session == null) {
        Toast.makeText(this, "Room belum aktif.", Toast.LENGTH_SHORT).show()
        return
    }
    val metadata = querySelectedFile(uri)
    if (!ServerFileRules.isAllowedChatAttachmentName(metadata.fileName)) {
        Toast.makeText(this, "Lampiran chat hanya untuk gambar, dokumen, teks, atau zip kecil.", Toast.LENGTH_LONG).show()
        return
    }
    if (metadata.sizeBytes !in 0..ServerFileRules.MAX_CHAT_ATTACHMENT_BYTES) {
        Toast.makeText(this, "Lampiran chat maksimal 10 MB.", Toast.LENGTH_LONG).show()
        return
    }
    val chatStats = controller.roomManager.chatAttachmentStorageStats()
    if (chatStats.totalBytes + metadata.sizeBytes > ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES) {
        Toast.makeText(this, "Storage lampiran chat room sudah penuh.", Toast.LENGTH_LONG).show()
        return
    }
    val hostName = session.hostName.ifBlank { getString(R.string.host_name_default) }
    val transfer = contentResolver.openInputStream(uri)?.use { input ->
        controller.fileStore.saveRoomFile(
            fileName = metadata.fileName,
            mimeType = metadata.mimeType,
            inputStream = input,
            roomId = session.sessionId,
            folderName = ServerFileRules.CHAT_DOWNLOADS_FOLDER,
            direction = TransferDirection.CHAT_ATTACHMENT,
            senderName = hostName
        )
    }
    if (transfer == null) {
        Toast.makeText(this, "Lampiran belum bisa dibaca.", Toast.LENGTH_SHORT).show()
        return
    }
    controller.roomManager.addTransfer(transfer)
    controller.roomManager.addMessage(
        senderId = currentHostId(),
        senderName = hostName,
        text = hostChatInput.text?.toString().orEmpty(),
        attachment = transfer
    )
    hostChatInput.text?.clear()
    controller.persistRecentTransfers()
    hostChatRenderer.forceScrollToBottom = true
    refreshHostDashboard()
    Toast.makeText(this, "Lampiran chat terkirim.", Toast.LENGTH_SHORT).show()
}

fun MainActivity.sendHostMessage() {
    val text = hostChatInput.text?.toString().orEmpty()
    val hostName = controller.roomManager.currentSession()?.hostName ?: getString(R.string.host_name_default)
    val message = controller.roomManager.addMessage(
        senderId = currentHostId(),
        senderName = hostName,
        text = text
    )
    if (message == null) {
        Toast.makeText(this, "Pesan masih kosong.", Toast.LENGTH_SHORT).show()
        return
    }
    hostChatInput.text?.clear()
    hostChatRenderer.forceScrollToBottom = true
    refreshHostDashboard()
}

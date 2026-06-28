package com.danis.nadi

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.network.server.ServerFileRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun MainActivity.sendHostChatMessage(text: String) {
    val session = controller.roomManager.currentSession()
    if (session == null) {
        Toast.makeText(this, "Room belum aktif.", Toast.LENGTH_SHORT).show()
        return
    }
    
    val attachmentUri = hostPendingAttachmentUri.value
    if (text.isEmpty() && attachmentUri == null) return

    val hostName = session.hostName.ifBlank { getString(R.string.host_name_default) }

    if (attachmentUri != null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val metadata = querySelectedFile(attachmentUri)
            if (!ServerFileRules.isAllowedChatAttachmentName(metadata.fileName)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@sendHostChatMessage, "Lampiran chat hanya untuk gambar, dokumen, teks, atau zip kecil.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            if (metadata.sizeBytes !in 0..ServerFileRules.MAX_CHAT_ATTACHMENT_BYTES) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@sendHostChatMessage, "Lampiran chat maksimal 10 MB.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val chatStats = controller.roomManager.chatAttachmentStorageStats()
            if (chatStats.totalBytes + metadata.sizeBytes > ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@sendHostChatMessage, "Storage lampiran chat room sudah penuh.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@sendHostChatMessage, "Mengirim lampiran...", Toast.LENGTH_SHORT).show()
            }
            
            val transfer = contentResolver.openInputStream(attachmentUri)?.use { input ->
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
            withContext(Dispatchers.Main) {
                if (transfer == null) {
                    Toast.makeText(this@sendHostChatMessage, "Lampiran gagal dibaca.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                controller.roomManager.addTransfer(transfer)
                controller.roomManager.addMessage(
                    senderId = currentHostId(),
                    senderName = hostName,
                    text = text,
                    attachment = transfer
                )
                hostPendingAttachmentUri.value = null
                controller.persistRecentTransfers()
                refreshHostDashboard()
                Toast.makeText(this@sendHostChatMessage, "Pesan lampiran terkirim.", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        val message = controller.roomManager.addMessage(
            senderId = currentHostId(),
            senderName = hostName,
            text = text
        )
        if (message == null) {
            Toast.makeText(this, "Pesan masih kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        refreshHostDashboard()
    }
}

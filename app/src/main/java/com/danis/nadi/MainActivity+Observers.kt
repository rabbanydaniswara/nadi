package com.danis.nadi

import androidx.lifecycle.lifecycleScope
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject

fun MainActivity.setupViewModelObservers() {
    // Observe host chat messages
    lifecycleScope.launch {
        hostViewModel.chatMessages.collectLatest { messages ->
            if (activeRoomPanel.visibility == android.view.View.VISIBLE) {
                hostChatRenderer.render(messages)
            }
        }
    }

    // Observe client chat messages
    lifecycleScope.launch {
        clientViewModel.chatMessages.collectLatest { messages ->
            if (activeClientRoomPanel.visibility == android.view.View.VISIBLE) {
                val allMessages = messages.toMutableList()
                val optimistic = clientChatMessages.filter { it.messageId.startsWith("opt_") }
                val filteredOptimistic = optimistic.filter { opt ->
                    messages.none { dbMsg ->
                        dbMsg.senderId == opt.senderId && dbMsg.text == opt.text
                    }
                }
                allMessages.addAll(filteredOptimistic)
                allMessages.sortBy { it.createdAt }
                
                val countBefore = clientChatMessages.size
                clientChatMessages.clear()
                clientChatMessages.addAll(allMessages)
                clientChatRenderer.render(clientChatMessages)

                if (clientChatMessages.size > countBefore) {
                    clientChatScrollView.post {
                        clientChatScrollView.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                }
            }
        }
    }

    // Observe client files
    lifecycleScope.launch {
        clientViewModel.sharedFiles.collectLatest { files ->
            if (activeClientRoomPanel.visibility == android.view.View.VISIBLE) {
                renderClientFiles(files.map { it.toJsonObject() })
            }
        }
    }
}

fun JSONObject.toTransferItem(): TransferItem {
    return TransferItem(
        transferId = getString("transferId"),
        fileName = getString("fileName"),
        mimeType = optString("mimeType").takeIf { it.isNotEmpty() },
        sizeBytes = getLong("sizeBytes"),
        direction = TransferDirection.DOWNLOAD,
        status = TransferStatus.PENDING,
        progress = 0,
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        localUri = null,
        senderName = optString("senderName", "Host")
    )
}

fun TransferItem.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("transferId", transferId)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("sizeBytes", sizeBytes)
        put("senderName", senderName ?: "Host")
        put("createdAt", createdAt)
    }
}

package com.danis.nadi

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.network.client.RoomClient
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.ui.compose.Screen
import org.json.JSONObject
import android.content.ClipboardManager

fun MainActivity.openJoinedRoom(url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    val valid = uri != null &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        uri.host?.isNotBlank() == true
    if (!valid) {
        Toast.makeText(this, "Masukkan URL room Nadi yang valid.", Toast.LENGTH_SHORT).show()
        return
    }
    clientRoomUrl = url
    currentScreenState.value = Screen.ClientJoinIdentity
}

fun MainActivity.submitClientIdentity(nim: String, name: String) {
    if (nim.isEmpty()) {
        Toast.makeText(this, "NIM tidak boleh kosong", Toast.LENGTH_SHORT).show()
        return
    }
    if (name.isEmpty()) {
        Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
        return
    }

    val prefs = getSharedPreferences("nadi_client_prefs", Context.MODE_PRIVATE)
    prefs.edit()
        .putString("client_nim", nim)
        .putString("client_name", name)
        .apply()

    var clientId = prefs.getString("client_id", null)
    if (clientId == null) {
        clientId = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", clientId).apply()
    }

    val url = clientRoomUrl ?: return
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val cleanBaseUrl = "${uri.scheme}://${uri.host}" + if (uri.port != -1) ":${uri.port}" else ""
    val token = uri.getQueryParameter("token")
    val pin = uri.getQueryParameter("pin")

    Toast.makeText(this, "Menghubungkan ke room...", Toast.LENGTH_SHORT).show()
    connectToRoomNatively(cleanBaseUrl, token, pin, clientId, name, nim)
}

fun MainActivity.connectToRoomNatively(
    cleanBaseUrl: String,
    token: String?,
    pin: String?,
    clientId: String,
    name: String,
    nim: String
) {
    val client = RoomClient(
        baseUrl = cleanBaseUrl,
        token = token,
        pin = pin,
        clientId = clientId,
        clientName = name,
        clientNim = nim
    )
    roomClient = client

    // Setup callbacks
    client.onConnectionStatusChanged = { status ->
        clientViewModel.connectionStatus.value = status
    }

    client.onMessageReceived = { message ->
        addRealMessage(message)
    }

    client.onFilesChanged = { filesList ->
        val transferItems = filesList.map { it.toTransferItem() }
        clientViewModel.addFiles(transferItems)
    }

    client.onRoomInfoChanged = { infoJson ->
        val roomName = infoJson.optString("roomName", "-")
        val hostName = infoJson.optString("hostName", "-")
        val clientCount = infoJson.optInt("clientCount", 0)
        clientViewModel.roomName.value = roomName
        clientViewModel.hostName.value = hostName
        clientViewModel.clientCount.value = clientCount
    }

    client.onReconnected = {
        fetchLatestClientChat()
    }

    // Authenticate
    client.authenticate { success, errorMsg ->
        if (success) {
            currentScreenState.value = Screen.ClientDashboard

            clientViewModel.selfNim.value = nim
            clientViewModel.selfName.value = name

            client.startWebSocket()
            client.fetchFiles()
            client.fetchRoomInfo()

            clientViewModel.loadRoomData(cleanBaseUrl)
            lifecycleScope.launch {
                val cached = chatRepository.getMessagesForRoomOnce(cleanBaseUrl)
                val lastTimestamp = cached.maxByOrNull { it.createdAt }?.createdAt ?: 0L
                client.fetchChatHistory(after = lastTimestamp) { messages ->
                    clientViewModel.addMessages(messages)
                    messages.forEach { ensureClientAttachmentTransfer(it) }
                }
            }

            startClientPolling()
        } else {
            if (errorMsg?.contains("invalid_token") == true || errorMsg?.contains("unauthorized") == true) {
                clientViewModel.pendingPinCallback = { enteredPin ->
                    client.pin = enteredPin
                    connectToRoomNatively(cleanBaseUrl, token, enteredPin, clientId, name, nim)
                }
                clientViewModel.showPinDialog.value = true
            } else {
                Toast.makeText(this, errorMsg ?: "Gagal masuk ke room", Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun MainActivity.confirmExitClientRoom() {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Keluar dari Room")
        .setMessage("Apakah Anda yakin ingin keluar dari room ini?")
        .setPositiveButton("Keluar") { _, _ -> closeClientRoom() }
        .setNegativeButton("Batal", null)
        .show()
}

fun MainActivity.closeClientRoom() {
    roomClient?.close()
    roomClient = null
    clientPollHandler.removeCallbacksAndMessages(null)
    clientViewModel.clearRoomData()
    clientTransfersMap.clear()
    clientPendingAttachmentUri = null
    currentScreenState.value = Screen.Join
}

fun MainActivity.startClientPolling() {
    clientPollHandler.removeCallbacksAndMessages(null)
    val runnable = object : Runnable {
        override fun run() {
            roomClient?.fetchFiles()
            roomClient?.fetchRoomInfo()
            fetchLatestClientChat()
            clientPollHandler.postDelayed(this, 5000)
        }
    }
    clientPollRunnable = runnable
    clientPollHandler.post(runnable)
}

fun MainActivity.handleClientFileUpload(uri: Uri, isAttachment: Boolean) {
    val client = roomClient ?: return
    val tempFile = copyUriToTempFile(uri)
    if (tempFile == null) {
        Toast.makeText(this, "Gagal memproses file.", Toast.LENGTH_SHORT).show()
        return
    }

    if (isAttachment) {
        handleClientChatAttachmentSelected(uri)
        tempFile.delete()
    } else {
        Toast.makeText(this, "Mengirim ${tempFile.name}...", Toast.LENGTH_SHORT).show()
        client.uploadFile(tempFile, isAttachment = false, text = null, onProgress = { progress ->
            // Attachment progress
        }, onFinished = { success, _ ->
            tempFile.delete()
            if (success) {
                Toast.makeText(this, "File berhasil dikirim ke room.", Toast.LENGTH_SHORT).show()
                client.fetchFiles()
            } else {
                Toast.makeText(this, "Gagal mengirim file.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

fun MainActivity.downloadClientSharedFile(
    transferId: String,
    fileName: String,
    mimeType: String?,
    senderName: String
) {
    val client = roomClient ?: return
    Toast.makeText(this, "Mengunduh $fileName...", Toast.LENGTH_SHORT).show()
    val tempDir = File(cacheDir, "downloads")
    client.downloadFile(transferId, fileName, tempDir) { success, tempFile ->
        if (success && tempFile != null) {
            try {
                tempFile.inputStream().use { input ->
                    controller.fileStore.saveRoomFile(
                        fileName = fileName,
                        mimeType = mimeType,
                        inputStream = input,
                        roomId = null,
                        folderName = "received",
                        direction = TransferDirection.DOWNLOAD,
                        senderName = senderName
                    )
                }
                tempFile.delete()
                Toast.makeText(this, "Unduhan selesai: $fileName", Toast.LENGTH_SHORT).show()
                client.fetchFiles()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Gagal menyimpan file.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Gagal mengunduh file.", Toast.LENGTH_SHORT).show()
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

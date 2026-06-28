package com.danis.nadi

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.network.client.RoomClient
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.ui.compose.Screen
import org.json.JSONObject
import android.content.ClipboardManager
import com.danis.nadi.util.NetworkBinder

fun MainActivity.openJoinedRoom(url: String) {
    val trimmed = url.trim()
    if (trimmed.isBlank()) {
        Toast.makeText(this, "Masukkan tautan room.", Toast.LENGTH_SHORT).show()
        return
    }
    
    // Auto-preprocess IP addresses or simple hosts
    val processedUrl = when {
        trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "http://$trimmed"
    }

    val uri = runCatching { Uri.parse(processedUrl) }.getOrNull()
    val valid = uri != null &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        uri.host?.isNotBlank() == true
    if (!valid) {
        Toast.makeText(this, "Masukkan URL room Nadi yang valid.", Toast.LENGTH_SHORT).show()
        return
    }
    
    // Bind process to Wi-Fi early and clear connection pool to prevent routing races
    NetworkBinder.bindToWifi(this)
    RoomClient.evictConnectionPool()

    clientRoomUrl = processedUrl
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
    // Bind network process to Wi-Fi to bypass cellular fallback in offline mode
    NetworkBinder.bindToWifi(this)
    RoomClient.evictConnectionPool()

    // Clear old client transfer maps to clean memory references
    clientTransfersMap.clear()

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

            // Fetch room metadata first to obtain the unique sessionId
            client.fetchRoomInfo { infoJson ->
                val sessionId = infoJson?.optString("sessionId")?.takeIf { it.isNotBlank() } ?: cleanBaseUrl

                // Load database messages for this unique session ID
                clientViewModel.loadRoomData(sessionId)

                client.startWebSocket()
                client.fetchFiles()

                lifecycleScope.launch {
                    val cached = chatRepository.getMessagesForRoomOnce(sessionId)
                    val lastTimestamp = cached.maxByOrNull { it.createdAt }?.createdAt ?: 0L
                    client.fetchChatHistory(after = lastTimestamp) { messages ->
                        clientViewModel.addMessages(messages)
                        messages.forEach { ensureClientAttachmentTransfer(it) }
                    }
                }

                startClientPolling()
            }
        } else {
            if (errorMsg?.contains("invalid_token") == true || errorMsg?.contains("unauthorized") == true) {
                clientViewModel.pendingPinCallback = { enteredPin ->
                    // Tutup RoomClient lama sebelum membuat yang baru untuk mencegah memory/connection leak
                    roomClient?.close()
                    roomClient = null
                    connectToRoomNatively(cleanBaseUrl, token, enteredPin, clientId, name, nim)
                }
                clientViewModel.showPinDialog.value = true
            } else {
                Toast.makeText(this, errorMsg ?: "Gagal masuk ke room", Toast.LENGTH_LONG).show()
                NetworkBinder.unbind(this@connectToRoomNatively)
            }
        }
    }
}

fun MainActivity.confirmExitClientRoom() {
    clientViewModel.showExitDialog.value = true
}

fun MainActivity.closeClientRoom() {
    NetworkBinder.unbind(this)
    roomClient?.close()
    roomClient = null
    clientPollHandler.removeCallbacksAndMessages(null)
    clientViewModel.clearRoomData()
    clientViewModel.showExitDialog.value = false
    clientTransfersMap.clear()
    clientPendingAttachmentUri.value = null
    currentScreenState.value = Screen.Join
}

fun MainActivity.startClientPolling() {
    clientPollHandler.removeCallbacksAndMessages(null)
    var filePollCounter = 0
    val runnable = object : Runnable {
        override fun run() {
            val isConnected = clientViewModel.connectionStatus.value == "Terhubung"
            if (isConnected) {
                // WebSocket aktif. Tidak perlu poll chat/info.
                // Cukup poll files setiap 15 detik (3 kali lipat dari 5 detik).
                filePollCounter++
                if (filePollCounter >= 3) {
                    roomClient?.fetchFiles()
                    filePollCounter = 0
                }
            } else {
                // WebSocket terputus. Lakukan full fallback polling setiap 5 detik.
                roomClient?.fetchFiles()
                roomClient?.fetchRoomInfo()
                fetchLatestClientChat()
                filePollCounter = 0
            }
            clientPollHandler.postDelayed(this, 5000)
        }
    }
    clientPollRunnable = runnable
    clientPollHandler.post(runnable)
}

fun MainActivity.handleClientFileUpload(uri: Uri, isAttachment: Boolean) {
    val client = roomClient ?: return
    if (isAttachment) {
        // Untuk attachment chat, cukup simpan URI — tidak perlu copy ke temp file
        handleClientChatAttachmentSelected(uri)
        return
    }
    // copyUriToTempFile melakukan ContentResolver I/O — dipindahkan ke Dispatchers.IO
    lifecycleScope.launch(Dispatchers.IO) {
        val tempFile = copyUriToTempFile(uri)
        if (tempFile == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@handleClientFileUpload, "Gagal memproses file.", Toast.LENGTH_SHORT).show()
            }
            return@launch
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(this@handleClientFileUpload, "Mengirim ${tempFile.name}...", Toast.LENGTH_SHORT).show()
            client.uploadFile(tempFile, isAttachment = false, text = null, onProgress = { _ ->
                // progress tidak ditampilkan saat ini
            }, onFinished = { success, _ ->
                tempFile.delete()
                if (success) {
                    Toast.makeText(this@handleClientFileUpload, "File berhasil dikirim ke room.", Toast.LENGTH_SHORT).show()
                    client.fetchFiles()
                } else {
                    Toast.makeText(this@handleClientFileUpload, "Gagal mengirim file.", Toast.LENGTH_SHORT).show()
                }
            })
        }
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
    // onFinished dipanggil di main thread; kita launch IO coroutine untuk saveRoomFile
    client.downloadFile(transferId, fileName, tempDir) { success, tempFile ->
        if (success && tempFile != null) {
            lifecycleScope.launch(Dispatchers.IO) {
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
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@downloadClientSharedFile, "Unduhan selesai: $fileName", Toast.LENGTH_SHORT).show()
                        client.fetchFiles()
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@downloadClientSharedFile, "Gagal menyimpan file.", Toast.LENGTH_SHORT).show()
                    }
                }
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

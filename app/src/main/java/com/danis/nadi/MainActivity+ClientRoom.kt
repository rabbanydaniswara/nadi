package com.danis.nadi

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.network.client.RoomClient
import java.util.UUID
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

fun MainActivity.pasteRoomUrl() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        .orEmpty()
        .trim()
    if (text.isBlank()) {
        Toast.makeText(this, "Clipboard belum berisi URL room.", Toast.LENGTH_SHORT).show()
        return
    }
    roomUrlInput.setText(text)
    roomUrlInput.setSelection(text.length)
}

fun MainActivity.openJoinedRoom() {
    val url = roomUrlInput.text?.toString().orEmpty().trim()
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    val valid = uri != null &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        uri.host?.isNotBlank() == true
    if (!valid) {
        Toast.makeText(this, "Masukkan URL room Nadi yang valid.", Toast.LENGTH_SHORT).show()
        return
    }
    clientRoomUrl = url
    showJoinIdentityScreen()
}

fun MainActivity.submitClientIdentity() {
    val nim = clientNimInput.text.toString().trim()
    val name = clientNameInput.text.toString().trim()
    if (nim.isEmpty()) {
        clientNimInput.error = "NIM tidak boleh kosong"
        clientNimInput.requestFocus()
        return
    }
    if (name.isEmpty()) {
        clientNameInput.error = "Nama tidak boleh kosong"
        clientNameInput.requestFocus()
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

    clientJoinButton.isEnabled = false
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
        clientInfoStatusText.text = status
        if (status == "Terhubung") {
            clientInfoStatusText.setTextColor(ContextCompat.getColor(this, R.color.nadi_green))
        } else {
            clientInfoStatusText.setTextColor(ContextCompat.getColor(this, R.color.nadi_error))
        }
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
        clientInfoRoomNameText.text = roomName
        clientInfoHostNameText.text = "Host: $hostName | Peserta: $clientCount"
    }

    client.onReconnected = {
        fetchLatestClientChat()
    }

    // Authenticate
    client.authenticate { success, errorMsg ->
        clientJoinButton.isEnabled = true
        if (success) {
            setupClientChatRenderer()
            showActiveClientRoom()

            clientInfoSelfIdentityText.text = "$nim - $name"

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
                promptClientForPin { enteredPin ->
                    client.pin = enteredPin
                    clientJoinButton.isEnabled = false
                    connectToRoomNatively(cleanBaseUrl, token, enteredPin, clientId, name, nim)
                }
            } else {
                Toast.makeText(this, errorMsg ?: "Gagal masuk ke room", Toast.LENGTH_LONG).show()
            }
        }
    }
}

fun MainActivity.promptClientForPin(onPinEntered: (String) -> Unit) {
    val input = EditText(this).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        hint = "Masukkan PIN Room"
        setPadding(24.dp(), 16.dp(), 24.dp(), 16.dp())
    }
    val container = FrameLayout(this).apply {
        addView(input, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(20.dp(), 8.dp(), 20.dp(), 8.dp())
        })
    }
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("PIN Diperlukan")
        .setMessage("Room ini dilindungi PIN. Silakan masukkan PIN room:")
        .setView(container)
        .setPositiveButton("Masuk") { _, _ ->
            val pin = input.text.toString().trim()
            if (pin.isNotEmpty()) {
                onPinEntered(pin)
            } else {
                Toast.makeText(this, "PIN tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton("Batal", null)
        .show()
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
    clientChatMessages.clear()
    clientTransfersMap.clear()
    clientPendingAttachmentUri = null
    showJoin()
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

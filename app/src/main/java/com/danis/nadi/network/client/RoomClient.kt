package com.danis.nadi.network.client

import android.os.Handler
import android.os.Looper
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class RoomClient(
    val baseUrl: String,
    val token: String?,
    var pin: String?,
    val clientId: String,
    val clientName: String,
    val clientNim: String
) {
    private val mainHandler: Handler? = try {
        Handler(Looper.getMainLooper())
    } catch (_: Exception) {
        null
    }
    companion object {
        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        fun evictConnectionPool() {
            try {
                sharedHttpClient.connectionPool.evictAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val httpClient = sharedHttpClient

    private var webSocket: WebSocket? = null
    private var isClosed = false
    private var hasConnectedBefore = false

    // Callbacks
    var onConnectionStatusChanged: ((String) -> Unit)? = null
    var onMessageReceived: ((ChatMessage) -> Unit)? = null
    var onFilesChanged: ((List<JSONObject>) -> Unit)? = null
    var onRoomInfoChanged: ((JSONObject) -> Unit)? = null
    var onReconnected: (() -> Unit)? = null

    private fun getAccessParams(): String {
        return when {
            !token.isNullOrBlank() -> "token=${UriEscape(token)}"
            !pin.isNullOrBlank() -> "pin=${UriEscape(pin!!)}"
            else -> ""
        }
    }

    private fun UriEscape(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }

    fun buildUrl(path: String, appendClient: Boolean = true): String {
        val query = mutableListOf<String>()
        val access = getAccessParams()
        if (access.isNotEmpty()) query.add(access)
        if (appendClient) query.add("clientId=$clientId")
        
        val queryString = if (query.isNotEmpty()) "?" + query.joinToString("&") else ""
        return "$baseUrl$path$queryString"
    }

    fun authenticate(onResult: (Boolean, String?) -> Unit) {
        val url = buildUrl("/api/identity", appendClient = false)
        val formBody = FormBody.Builder()
            .add("clientId", clientId)
            .add("nim", clientNim)
            .add("name", clientName)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain { onResult(false, e.localizedMessage ?: "Koneksi gagal") }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    runOnMain { onResult(true, null) }
                } else {
                    val errorMsg = try {
                        JSONObject(body).optString("error", "Gagal masuk ke room")
                    } catch (_: Exception) {
                        "Gagal masuk ke room (${response.code})"
                    }
                    runOnMain { onResult(false, errorMsg) }
                }
            }
        })
    }

    fun startWebSocket() {
        if (isClosed) return
        val wsUrl = buildUrl("/ws/chat")
            .replace("http://", "ws://")
            .replace("https://", "wss://")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnMain {
                    onConnectionStatusChanged?.invoke("Terhubung")
                    if (hasConnectedBefore) {
                        onReconnected?.invoke()
                    }
                    hasConnectedBefore = true
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    if (type == "chat_messages") {
                        val messagesArray = json.optJSONArray("messages") ?: return
                        for (i in 0 until messagesArray.length()) {
                            val msgJson = messagesArray.getJSONObject(i)
                            val message = parseChatMessage(msgJson)
                            runOnMain { onMessageReceived?.invoke(message) }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnMain { onConnectionStatusChanged?.invoke("Terputus") }
                reconnectLater()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnMain { onConnectionStatusChanged?.invoke("Koneksi gagal") }
                reconnectLater()
            }
        })
    }

    private fun reconnectLater() {
        if (isClosed) return
        mainHandler?.postDelayed({
            if (!isClosed) {
                runOnMain { onConnectionStatusChanged?.invoke("Menghubungkan ulang...") }
                startWebSocket()
            }
        }, 5000)
    }

    fun fetchFiles() {
        val url = buildUrl("/api/files")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    try {
                        val json = JSONObject(body)
                        val filesArray = json.getJSONArray("files")
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until filesArray.length()) {
                            list.add(filesArray.getJSONObject(i))
                        }
                        runOnMain { onFilesChanged?.invoke(list) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    fun fetchRoomInfo(onResult: ((JSONObject?) -> Unit)? = null) {
        val url = buildUrl("/api/room")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain { onResult?.invoke(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    try {
                        val json = JSONObject(body)
                        runOnMain {
                            onRoomInfoChanged?.invoke(json)
                            onResult?.invoke(json)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnMain { onResult?.invoke(null) }
                    }
                } else {
                    runOnMain { onResult?.invoke(null) }
                }
            }
        })
    }

    fun fetchChatHistory(after: Long = 0L, onResult: (List<ChatMessage>) -> Unit) {
        val url = buildUrl("/api/chat") + "&after=$after"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain { onResult(emptyList()) }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    try {
                        val json = JSONObject(body)
                        val messagesArray = json.getJSONArray("messages")
                        val list = mutableListOf<ChatMessage>()
                        for (i in 0 until messagesArray.length()) {
                            list.add(parseChatMessage(messagesArray.getJSONObject(i)))
                        }
                        runOnMain { onResult(list) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnMain { onResult(emptyList()) }
                    }
                } else {
                    runOnMain { onResult(emptyList()) }
                }
            }
        })
    }

    fun sendChatMessage(text: String, onResult: (Boolean) -> Unit) {
        val url = buildUrl("/api/chat")
        val formBody = FormBody.Builder()
            .add("clientId", clientId)
            .add("text", text)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain { onResult(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnMain { onResult(response.isSuccessful) }
            }
        })
    }

    fun uploadFile(file: File, isAttachment: Boolean, text: String? = null, onProgress: (Int) -> Unit, onFinished: (Boolean, String?) -> Unit) {
        val path = if (isAttachment) "/api/chat-attachment" else "/api/upload"
        val url = buildUrl(path)

        // Custom RequestBody to support progress tracking
        val fileBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
            override fun contentLength() = file.length()
            override fun writeTo(sink: okio.BufferedSink) {
                val buffer = ByteArray(2048)
                var uploaded = 0L
                val fileSize = file.length()
                file.inputStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        uploaded += read
                        val progress = ((uploaded * 100) / fileSize).toInt()
                        runOnMain { onProgress(progress) }
                    }
                }
            }
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("clientId", clientId)
            .apply {
                if (text != null) {
                    addFormDataPart("text", text)
                }
            }
            .addFormDataPart("file", file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain { onFinished(false, e.localizedMessage) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(body)
                        val messageJson = json.optJSONObject("message")
                        val msgId = messageJson?.optString("messageId")
                        runOnMain { onFinished(true, msgId) }
                    } catch (_: Exception) {
                        runOnMain { onFinished(true, null) }
                    }
                } else {
                    runOnMain { onFinished(false, "Upload gagal (${response.code})") }
                }
            }
        })
    }

    fun downloadFile(transferId: String, fileName: String, outputDir: File, onFinished: (Boolean, File?) -> Unit) {
        val url = buildUrl("/api/download/$transferId")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain { onFinished(false, null) }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnMain { onFinished(false, null) }
                    return
                }
                var outputFile: File? = null
                try {
                    if (!outputDir.exists()) outputDir.mkdirs()
                    outputFile = File(outputDir, fileName)
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    runOnMain { onFinished(true, outputFile) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    try { outputFile?.delete() } catch (_: Exception) {}
                    runOnMain { onFinished(false, null) }
                }
            }
        })
    }

    fun close() {
        isClosed = true
        webSocket?.close(1000, "Client closed")
    }

    private fun runOnMain(action: () -> Unit) {
        val handler = mainHandler
        if (handler != null) {
            handler.post(action)
        } else {
            action()
        }
    }

    private fun parseChatMessage(json: JSONObject): ChatMessage {
        return ChatMessage(
            messageId = json.getString("messageId"),
            senderId = json.getString("senderId"),
            senderName = json.getString("senderName"),
            text = json.getString("text"),
            createdAt = json.getLong("createdAt"),
            status = json.optString("status", "sent"),
            attachmentTransferId = json.optString("attachmentTransferId").takeIf { it.isNotBlank() },
            attachmentFileName = json.optString("attachmentFileName").takeIf { it.isNotBlank() },
            attachmentMimeType = json.optString("attachmentMimeType").takeIf { it.isNotBlank() },
            attachmentSizeBytes = json.optLong("attachmentSizeBytes", -1L),
            attachmentStatus = json.optString("attachmentStatus", "pending")
        )
    }
}

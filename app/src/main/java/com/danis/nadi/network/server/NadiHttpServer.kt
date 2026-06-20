package com.danis.nadi.network.server

import com.danis.nadi.file.FileStore
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

class NadiHttpServer(
    port: Int,
    private val roomManager: RoomManager,
    private val fileStore: FileStore
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/health" -> {
                    text(Response.Status.OK, "Nadi room server is running.")
                }
                session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") -> {
                    html(Response.Status.OK, BrowserClientAssets.html())
                }
                session.method == Method.GET && session.uri == "/api/room" -> {
                    roomMetadata(session)
                }
                session.method == Method.GET && session.uri == "/api/files" -> {
                    fileList(session)
                }
                session.method == Method.GET && session.uri.startsWith("/api/download/") -> {
                    downloadFile(session)
                }
                session.method == Method.POST && session.uri == "/api/upload" -> {
                    uploadFile(session)
                }
                session.method == Method.GET && session.uri == "/api/chat" -> {
                    listChat(session)
                }
                session.method == Method.POST && session.uri == "/api/chat" -> {
                    sendChat(session)
                }
                else -> {
                    json(Response.Status.NOT_FOUND, """{"error":"not_found"}""")
                }
            }
        } catch (_: Exception) {
            json(Response.Status.INTERNAL_ERROR, """{"error":"server_error"}""")
        }
    }

    private fun roomMetadata(session: IHTTPSession): Response {
        val token = session.parameters["token"]?.firstOrNull()
        if (!roomManager.validateToken(token)) return invalidToken()
        roomManager.touchClient(
            displayName = session.parameters["name"]?.firstOrNull().orEmpty(),
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        )

        val snapshot = roomManager.snapshot()
        val room = snapshot.session ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"room_not_found"}"""
        )
        return json(Response.Status.OK, room.toJson(snapshot.clients.size))
    }

    private fun fileList(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val shared = roomManager.sharedFiles()
        return json(Response.Status.OK, """{"files":${shared.toFileJsonArray()}}""")
    }

    private fun downloadFile(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val id = session.uri.removePrefix("/api/download/").decodeUrl()
        val transfer = roomManager.transferById(id) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_not_found"}"""
        )
        if (transfer.direction != TransferDirection.SHARED) {
            return json(Response.Status.NOT_FOUND, """{"error":"file_not_shared"}""")
        }
        val payload = fileStore.openForDownload(transfer) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_unavailable"}"""
        )
        val response = if (payload.sizeBytes >= 0) {
            newFixedLengthResponse(
                Response.Status.OK,
                payload.mimeType,
                payload.inputStream,
                payload.sizeBytes
            )
        } else {
            newChunkedResponse(Response.Status.OK, payload.mimeType, payload.inputStream)
        }
        response.addHeader("Content-Disposition", "attachment; filename=\"${payload.fileName.headerSafe()}\"")
        return response
    }

    private fun uploadFile(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val tempPath = files["file"] ?: files.values.firstOrNull()
        if (tempPath.isNullOrBlank()) {
            return json(Response.Status.BAD_REQUEST, """{"error":"file_required"}""")
        }
        val originalName = session.parameters["file"]?.firstOrNull()
            ?: session.parameters["filename"]?.firstOrNull()
            ?: "upload.bin"
        val mimeType = session.headers["content-type"]?.substringAfter("type=", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        val transfer = FileInputStream(File(tempPath)).use { input ->
            fileStore.saveUpload(originalName, mimeType, input)
        }
        roomManager.addTransfer(transfer)
        return json(Response.Status.OK, """{"file":${transfer.toJson()}}""")
    }

    private fun listChat(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val after = session.parameters["after"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val messages = roomManager.messagesAfter(after)
        return json(Response.Status.OK, """{"messages":${messages.toMessageJsonArray()}}""")
    }

    private fun sendChat(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        session.parseBody(mutableMapOf())
        val senderName = session.parameters["senderName"]?.firstOrNull() ?: "Browser"
        val text = session.parameters["text"]?.firstOrNull().orEmpty()
        val message = roomManager.addMessage(
            senderId = "browser-${session.remoteIpAddress.orEmpty()}",
            senderName = senderName,
            text = text
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"message_required"}""")
        return json(Response.Status.OK, """{"message":${message.toJson()}}""")
    }

    private fun RoomSession.toJson(clientCount: Int): String {
        return buildString {
            append("{")
            append("\"sessionId\":\"").append(sessionId.escapeJson()).append("\",")
            append("\"roomName\":\"").append(roomName.escapeJson()).append("\",")
            append("\"hostName\":\"").append(hostName.escapeJson()).append("\",")
            append("\"status\":\"").append(status.name.lowercase()).append("\",")
            append("\"localUrl\":\"").append((localUrl ?: "").escapeJson()).append("\",")
            append("\"clientCount\":").append(clientCount).append(",")
            append("\"startedAt\":").append(startedAt)
            append("}")
        }
    }

    private fun List<TransferItem>.toFileJsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.toJson() }

    private fun TransferItem.toJson(): String {
        return buildString {
            append("{")
            append("\"transferId\":\"").append(transferId.escapeJson()).append("\",")
            append("\"fileName\":\"").append(fileName.escapeJson()).append("\",")
            append("\"mimeType\":\"").append((mimeType ?: "").escapeJson()).append("\",")
            append("\"sizeBytes\":").append(sizeBytes).append(",")
            append("\"direction\":\"").append(direction.name.lowercase()).append("\",")
            append("\"status\":\"").append(status.name.lowercase()).append("\",")
            append("\"progress\":").append(progress).append(",")
            append("\"createdAt\":").append(createdAt).append(",")
            append("\"senderName\":\"").append((senderName ?: "").escapeJson()).append("\"")
            append("}")
        }
    }

    private fun List<ChatMessage>.toMessageJsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.toJson() }

    private fun ChatMessage.toJson(): String {
        return buildString {
            append("{")
            append("\"messageId\":\"").append(messageId.escapeJson()).append("\",")
            append("\"senderId\":\"").append(senderId.escapeJson()).append("\",")
            append("\"senderName\":\"").append(senderName.escapeJson()).append("\",")
            append("\"text\":\"").append(text.escapeJson()).append("\",")
            append("\"createdAt\":").append(createdAt).append(",")
            append("\"status\":\"").append(status.escapeJson()).append("\"")
            append("}")
        }
    }

    private fun invalidToken(): Response = json(Response.Status.UNAUTHORIZED, """{"error":"invalid_token"}""")

    private fun text(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, body)
    }

    private fun html(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, "text/html; charset=utf-8", body)
            .withNoStoreHeaders()
    }

    private fun json(status: Response.IStatus, body: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
            .withNoStoreHeaders()
    }

    private fun Response.withNoStoreHeaders(): Response {
        addHeader("Cache-Control", "no-store, max-age=0")
        addHeader("Pragma", "no-cache")
        addHeader("Expires", "0")
        addHeader("X-Content-Type-Options", "nosniff")
        return this
    }

    private fun String.escapeJson(): String {
        return buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun String.decodeUrl(): String = URLDecoder.decode(this, "UTF-8")

    private fun String.headerSafe(): String = replace("\"", "'").replace("\r", "").replace("\n", "")
}

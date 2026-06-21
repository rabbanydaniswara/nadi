package com.danis.nadi.network.server

import com.danis.nadi.file.FileStore
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
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
                session.method == Method.POST && session.uri == "/api/identity" -> {
                    registerIdentity(session)
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
                session.method == Method.POST && session.uri == "/api/chat-attachment" -> {
                    sendChatAttachment(session)
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
        val client = roomManager.touchKnownClient(
            clientId = session.parameters["clientId"]?.firstOrNull(),
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        )

        val snapshot = roomManager.snapshot()
        val room = snapshot.session ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"room_not_found"}"""
        )
        return json(Response.Status.OK, room.toJson(snapshot.clients.size, identityRequired = client == null))
    }

    private fun registerIdentity(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        session.parseBody(mutableMapOf())
        val client = roomManager.touchIdentifiedClient(
            clientId = session.parameters["clientId"]?.firstOrNull().orEmpty(),
            nim = session.parameters["nim"]?.firstOrNull().orEmpty(),
            name = session.parameters["name"]?.firstOrNull().orEmpty(),
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"invalid_identity"}""")
        return json(Response.Status.OK, """{"client":${client.toJson()}}""")
    }

    private fun fileList(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        if (identifiedClient(session) == null) return identityRequired()
        val shared = roomManager.sharedFiles()
        return json(Response.Status.OK, """{"files":${shared.toFileJsonArray()}}""")
    }

    private fun downloadFile(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        if (identifiedClient(session) == null) return identityRequired()
        val id = session.uri.removePrefix("/api/download/").decodeUrl()
        val transfer = roomManager.transferById(id) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_not_found"}"""
        )
        if (transfer.direction != TransferDirection.SHARED && transfer.direction != TransferDirection.CHAT_ATTACHMENT) {
            return json(Response.Status.NOT_FOUND, """{"error":"file_not_downloadable"}""")
        }
        val payload = fileStore.openForDownload(transfer) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_unavailable"}"""
        )
        val preview = session.parameters["preview"]?.firstOrNull() == "1" &&
            transfer.direction == TransferDirection.CHAT_ATTACHMENT &&
            (payload.mimeType.isPreviewableImage() || payload.fileName.isPreviewableImageName())
        val responseMimeType = if (preview) {
            payload.mimeType.takeIf { it.isPreviewableImage() } ?: payload.fileName.inferredMimeType()
        } else {
            payload.mimeType
        }
        val previewEtag = if (preview) transfer.previewEtag() else null
        if (previewEtag != null && session.matchesIfNoneMatch(previewEtag)) {
            payload.inputStream.close()
            return newFixedLengthResponse(Response.Status.NOT_MODIFIED, responseMimeType, "")
                .withPreviewCacheHeaders(previewEtag)
        }
        val response = if (payload.sizeBytes >= 0) {
            newFixedLengthResponse(
                Response.Status.OK,
                responseMimeType,
                payload.inputStream,
                payload.sizeBytes
            )
        } else {
            newChunkedResponse(Response.Status.OK, responseMimeType, payload.inputStream)
        }
        val disposition = if (preview) "inline" else "attachment"
        response.addHeader("Content-Disposition", "$disposition; filename=\"${payload.fileName.headerSafe()}\"")
        return if (previewEtag != null) {
            response.withPreviewCacheHeaders(previewEtag)
        } else {
            response.withNoStoreHeaders()
        }
    }

    private fun uploadFile(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val client = identifiedClient(session, session.clientIdParameter()) ?: return identityRequired()
        val tempPath = files["file"] ?: files.values.firstOrNull()
        if (tempPath.isNullOrBlank()) {
            return json(Response.Status.BAD_REQUEST, """{"error":"file_required"}""")
        }
        val originalName = session.parameters["file"]?.firstOrNull()
            ?: session.parameters["filename"]?.firstOrNull()
            ?: "upload.bin"
        val mimeType = originalName.resolvedUploadMimeType(session.headers["content-type"]?.substringAfter("type=", missingDelimiterValue = ""))
        val roomId = roomManager.currentSession()?.sessionId
        val transfer = FileInputStream(File(tempPath)).use { input ->
            fileStore.saveRoomFile(
                fileName = originalName,
                mimeType = mimeType,
                inputStream = input,
                roomId = roomId,
                folderName = "received",
                direction = TransferDirection.UPLOAD,
                senderName = client.displayName
            )
        }
        roomManager.addTransfer(transfer)
        return json(Response.Status.OK, """{"file":${transfer.toJson()}}""")
    }

    private fun listChat(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        if (identifiedClient(session) == null) return identityRequired()
        val after = session.parameters["after"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val messages = roomManager.messagesAfter(after)
        return json(Response.Status.OK, """{"messages":${messages.toMessageJsonArray()}}""")
    }

    private fun sendChat(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        session.parseBody(mutableMapOf())
        val client = identifiedClient(session) ?: return identityRequired()
        val text = session.parameters["text"]?.firstOrNull().orEmpty()
        val message = roomManager.addMessage(
            senderId = client.clientId,
            senderName = client.displayName,
            text = text
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"message_required"}""")
        return json(Response.Status.OK, """{"message":${message.toJson()}}""")
    }

    private fun sendChatAttachment(session: IHTTPSession): Response {
        if (!roomManager.validateToken(session.parameters["token"]?.firstOrNull())) return invalidToken()
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val client = identifiedClient(session, session.clientIdParameter()) ?: return identityRequired()
        val tempPath = files["file"] ?: files.values.firstOrNull()
        if (tempPath.isNullOrBlank()) {
            return json(Response.Status.BAD_REQUEST, """{"error":"file_required"}""")
        }
        val originalName = session.parameters["file"]?.firstOrNull()
            ?: session.parameters["filename"]?.firstOrNull()
            ?: "attachment.bin"
        val tempFile = File(tempPath)
        if (!originalName.isAllowedChatAttachmentName() || tempFile.length() > MAX_CHAT_ATTACHMENT_BYTES) {
            return json(Response.Status.BAD_REQUEST, """{"error":"attachment_not_allowed"}""")
        }
        val mimeType = originalName.resolvedUploadMimeType(session.headers["content-type"]?.substringAfter("type=", missingDelimiterValue = ""))
        val roomId = roomManager.currentSession()?.sessionId
        val transfer = FileInputStream(tempFile).use { input ->
            fileStore.saveRoomFile(
                fileName = originalName,
                mimeType = mimeType,
                inputStream = input,
                roomId = roomId,
                folderName = "chat-downloads",
                direction = TransferDirection.CHAT_ATTACHMENT,
                senderName = client.displayName
            )
        }
        roomManager.addTransfer(transfer)
        val text = session.parameters["text"]?.firstOrNull().orEmpty()
        val message = roomManager.addMessage(
            senderId = client.clientId,
            senderName = client.displayName,
            text = text.ifBlank { "Mengirim lampiran ${transfer.fileName}" },
            attachment = transfer
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"message_required"}""")
        return json(Response.Status.OK, """{"message":${message.toJson()},"file":${transfer.toJson()}}""")
    }

    private fun RoomSession.toJson(clientCount: Int, identityRequired: Boolean): String {
        return buildString {
            append("{")
            append("\"sessionId\":\"").append(sessionId.escapeJson()).append("\",")
            append("\"roomName\":\"").append(roomName.escapeJson()).append("\",")
            append("\"hostName\":\"").append(hostName.escapeJson()).append("\",")
            append("\"status\":\"").append(status.name.lowercase()).append("\",")
            append("\"localUrl\":\"").append((localUrl ?: "").escapeJson()).append("\",")
            append("\"clientCount\":").append(clientCount).append(",")
            append("\"identityRequired\":").append(identityRequired).append(",")
            append("\"startedAt\":").append(startedAt)
            append("}")
        }
    }

    private fun ConnectedClient.toJson(): String {
        return buildString {
            append("{")
            append("\"clientId\":\"").append(clientId.escapeJson()).append("\",")
            append("\"nim\":\"").append(nim.escapeJson()).append("\",")
            append("\"name\":\"").append(name.escapeJson()).append("\",")
            append("\"displayName\":\"").append(displayName.escapeJson()).append("\",")
            append("\"joinedAt\":").append(joinedAt).append(",")
            append("\"lastSeenAt\":").append(lastSeenAt)
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
            append("\"status\":\"").append(status.escapeJson()).append("\",")
            append("\"attachmentTransferId\":\"").append((attachmentTransferId ?: "").escapeJson()).append("\",")
            append("\"attachmentFileName\":\"").append((attachmentFileName ?: "").escapeJson()).append("\",")
            append("\"attachmentMimeType\":\"").append((attachmentMimeType ?: "").escapeJson()).append("\",")
            append("\"attachmentSizeBytes\":").append(attachmentSizeBytes)
            append("}")
        }
    }

    private fun invalidToken(): Response = json(Response.Status.UNAUTHORIZED, """{"error":"invalid_token"}""")

    private fun identityRequired(): Response = json(Response.Status.FORBIDDEN, """{"error":"identity_required"}""")

    private fun identifiedClient(session: IHTTPSession, clientId: String? = session.clientIdParameter()): ConnectedClient? {
        return roomManager.touchKnownClient(
            clientId = clientId,
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        )
    }

    private fun IHTTPSession.clientIdParameter(): String? {
        return parameters["clientId"]?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

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

    private fun Response.withPreviewCacheHeaders(etag: String): Response {
        addHeader("Cache-Control", "public, max-age=31536000, immutable")
        addHeader("ETag", etag)
        addHeader("X-Content-Type-Options", "nosniff")
        return this
    }

    private fun TransferItem.previewEtag(): String = "\"preview-$transferId-$sizeBytes\""

    private fun IHTTPSession.matchesIfNoneMatch(etag: String): Boolean {
        val header = headers["if-none-match"] ?: headers["If-None-Match"] ?: return false
        return header.split(",").any { value ->
            val candidate = value.trim()
            candidate == "*" || candidate == etag
        }
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

    private fun String.isPreviewableImage(): Boolean = lowercase() in setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    private fun String.isPreviewableImageName(): Boolean {
        return substringAfterLast('.', missingDelimiterValue = "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private fun String.resolvedUploadMimeType(declaredMimeType: String?): String {
        return declaredMimeType
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "application/octet-stream" && !it.startsWith("multipart/") }
            ?: inferredMimeType()
    }

    private fun String.inferredMimeType(): String {
        return when (substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun String.isAllowedChatAttachmentName(): Boolean {
        val extension = substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in ALLOWED_CHAT_ATTACHMENT_EXTENSIONS
    }

    private companion object {
        const val MAX_CHAT_ATTACHMENT_BYTES = 10L * 1024L * 1024L
        val ALLOWED_CHAT_ATTACHMENT_EXTENSIONS = setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp",
            "pdf",
            "txt",
            "doc",
            "docx",
            "ppt",
            "pptx",
            "xls",
            "xlsx",
            "zip"
        )
    }
}

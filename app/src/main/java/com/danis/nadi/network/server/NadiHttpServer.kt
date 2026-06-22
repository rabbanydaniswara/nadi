package com.danis.nadi.network.server

import com.danis.nadi.file.FileStore
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoWSD
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

class NadiHttpServer(
    port: Int,
    private val roomManager: RoomManager,
    private val fileStore: FileStore,
    private val maxFileRoomUploadBytes: Long = ServerFileRules.MAX_FILE_ROOM_UPLOAD_BYTES,
    private val maxChatAttachmentStorageBytes: Long = ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val browserClientHtml: () -> String = BrowserClientAssets::html,
    private val browserClientAsset: (String) -> BrowserClientAsset? = BrowserClientAssets::asset
) : NanoWSD(port) {

    private val chatWebSocketHub = ChatWebSocketHub(
        path = CHAT_WEBSOCKET_PATH,
        canOpenSession = { session -> hasRoomAccess(session) && identifiedClient(session) != null },
        touchSession = { session -> identifiedClient(session) }
    )
    private val chatMessageSubscription = roomManager.addMessageListener { message ->
        chatWebSocketHub.broadcast(listOf(message))
    }

    override fun stop() {
        chatMessageSubscription.close()
        chatWebSocketHub.close()
        super.stop()
    }

    override fun serveHttp(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/health" -> {
                    text(Response.Status.OK, "Nadi room server is running.")
                }
                session.method == Method.GET && (session.uri == "/" || session.uri == "/index.html") -> {
                    html(Response.Status.OK, browserClientHtml())
                }
                session.method == Method.GET && session.uri.startsWith("/assets/") -> {
                    browserAsset(session)
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

    private fun browserAsset(session: IHTTPSession): Response {
        val fileName = session.uri.removePrefix("/assets/")
        if (fileName.isBlank() || "/" in fileName) {
            return json(Response.Status.NOT_FOUND, """{"error":"asset_not_found"}""")
        }
        val asset = browserClientAsset(fileName) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"asset_not_found"}"""
        )
        return newFixedLengthResponse(Response.Status.OK, asset.mimeType, asset.content)
            .withNoStoreHeaders()
    }

    private fun roomMetadata(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
        cleanupExpiredChatAttachments()
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
        return json(
            Response.Status.OK,
            NadiJson.roomSession(
                room = room,
                clientCount = snapshot.clients.size,
                identityRequired = client == null,
                chatAttachmentStorageStats = roomManager.chatAttachmentStorageStats(),
                maxChatAttachmentStorageBytes = maxChatAttachmentStorageBytes
            )
        )
    }

    private fun registerIdentity(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
        session.parseBody(mutableMapOf())
        val client = roomManager.touchIdentifiedClient(
            clientId = session.parameters["clientId"]?.firstOrNull().orEmpty(),
            nim = session.parameters["nim"]?.firstOrNull().orEmpty(),
            name = session.parameters["name"]?.firstOrNull().orEmpty(),
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"invalid_identity"}""")
        return json(Response.Status.OK, """{"client":${NadiJson.client(client)}}""")
    }

    private fun fileList(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
        if (identifiedClient(session) == null) return identityRequired()
        val shared = roomManager.sharedFiles()
        return json(Response.Status.OK, """{"files":${NadiJson.transferArray(shared)}}""")
    }

    private fun downloadFile(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
        cleanupExpiredChatAttachments()
        if (identifiedClient(session) == null) return identityRequired()
        val id = session.uri.removePrefix("/api/download/").decodeUrl()
        val transfer = roomManager.transferById(id) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_not_found"}"""
        )
        if (transfer.direction != TransferDirection.SHARED && transfer.direction != TransferDirection.CHAT_ATTACHMENT) {
            return json(Response.Status.NOT_FOUND, """{"error":"file_not_downloadable"}""")
        }
        if (transfer.status == com.danis.nadi.model.TransferStatus.EXPIRED) {
            return json(Response.Status.GONE, """{"error":"file_expired"}""")
        }
        val payload = fileStore.openForDownload(transfer) ?: return json(
            Response.Status.NOT_FOUND,
            """{"error":"file_unavailable"}"""
        )
        if (transfer.direction == TransferDirection.CHAT_ATTACHMENT) {
            roomManager.markTransferDownloaded(transfer.transferId)
        }
        val preview = session.parameters["preview"]?.firstOrNull() == "1" &&
            transfer.direction == TransferDirection.CHAT_ATTACHMENT &&
            (ServerFileRules.isPreviewableImageMime(payload.mimeType) ||
                ServerFileRules.isPreviewableImageName(payload.fileName))
        val responseMimeType = if (preview) {
            payload.mimeType.takeIf { ServerFileRules.isPreviewableImageMime(it) }
                ?: ServerFileRules.inferredMimeType(payload.fileName)
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

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return chatWebSocketHub.open(handshake)
    }

    private fun uploadFile(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
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
        if (!ServerFileRules.isSafeOriginalFileName(originalName)) {
            return json(Response.Status.BAD_REQUEST, """{"error":"unsafe_file_name"}""")
        }
        val tempFile = File(tempPath)
        if (tempFile.length() > maxFileRoomUploadBytes) {
            return json(
                Response.Status.BAD_REQUEST,
                """{"error":"file_too_large","maxBytes":$maxFileRoomUploadBytes}"""
            )
        }
        val mimeType = ServerFileRules.resolvedUploadMimeType(
            fileName = originalName,
            declaredMimeType = session.headers["content-type"]?.substringAfter("type=", missingDelimiterValue = "")
        )
        val roomId = roomManager.currentSession()?.sessionId
        val transfer = FileInputStream(tempFile).use { input ->
            fileStore.saveRoomFile(
                fileName = originalName,
                mimeType = mimeType,
                inputStream = input,
                roomId = roomId,
                folderName = ServerFileRules.FILE_ROOM_RECEIVED_FOLDER,
                direction = TransferDirection.UPLOAD,
                senderName = client.displayName
            )
        }
        roomManager.addTransfer(transfer)
        return json(Response.Status.OK, """{"file":${NadiJson.transfer(transfer)}}""")
    }

    private fun listChat(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
        if (identifiedClient(session) == null) return identityRequired()
        val hadCleanup = cleanupExpiredChatAttachments()
        val after = if (hadCleanup) {
            0L
        } else {
            session.parameters["after"]?.firstOrNull()?.toLongOrNull() ?: 0L
        }
        val messages = roomManager.messagesAfter(after)
        return json(Response.Status.OK, """{"messages":${NadiJson.messageArray(messages)}}""")
    }

    private fun sendChat(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
        session.parseBody(mutableMapOf())
        val client = identifiedClient(session) ?: return identityRequired()
        val text = session.parameters["text"]?.firstOrNull().orEmpty()
        val message = roomManager.addMessage(
            senderId = client.clientId,
            senderName = client.displayName,
            text = text
        ) ?: return json(Response.Status.BAD_REQUEST, """{"error":"message_required"}""")
        return json(Response.Status.OK, """{"message":${NadiJson.message(message)}}""")
    }

    private fun sendChatAttachment(session: IHTTPSession): Response {
        if (!hasRoomAccess(session)) return invalidToken()
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
        if (!ServerFileRules.isAllowedChatAttachment(originalName, tempFile.length())) {
            return json(Response.Status.BAD_REQUEST, """{"error":"attachment_not_allowed"}""")
        }
        cleanupExpiredChatAttachments()
        val storageStats = roomManager.chatAttachmentStorageStats()
        if (storageStats.totalBytes + tempFile.length() > maxChatAttachmentStorageBytes) {
            return json(
                Response.Status.BAD_REQUEST,
                """{"error":"chat_attachment_storage_full","maxBytes":$maxChatAttachmentStorageBytes}"""
            )
        }
        val mimeType = ServerFileRules.resolvedUploadMimeType(
            fileName = originalName,
            declaredMimeType = session.headers["content-type"]?.substringAfter("type=", missingDelimiterValue = "")
        )
        val roomId = roomManager.currentSession()?.sessionId
        val transfer = FileInputStream(tempFile).use { input ->
            fileStore.saveRoomFile(
                fileName = originalName,
                mimeType = mimeType,
                inputStream = input,
                roomId = roomId,
                folderName = ServerFileRules.CHAT_DOWNLOADS_FOLDER,
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
        return json(Response.Status.OK, """{"message":${NadiJson.message(message)},"file":${NadiJson.transfer(transfer)}}""")
    }

    private fun invalidToken(): Response = json(Response.Status.UNAUTHORIZED, """{"error":"invalid_token"}""")

    private fun identityRequired(): Response = json(Response.Status.FORBIDDEN, """{"error":"identity_required"}""")

    private fun hasRoomAccess(session: IHTTPSession): Boolean {
        return roomManager.validateAccess(
            token = session.parameters["token"]?.firstOrNull(),
            pin = session.parameters["pin"]?.firstOrNull()
        )
    }

    private fun identifiedClient(session: IHTTPSession, clientId: String? = session.clientIdParameter()): ConnectedClient? {
        return roomManager.touchKnownClient(
            clientId = clientId,
            userAgent = session.headers["user-agent"].orEmpty(),
            ipAddress = session.remoteIpAddress.orEmpty()
        )
    }

    private fun cleanupExpiredChatAttachments(): Boolean {
        val cutoff = clock() - ServerFileRules.CHAT_ATTACHMENT_TTL_MILLIS
        val expired = roomManager.expireChatAttachmentsOlderThan(cutoff)
        expired.forEach { transfer ->
            fileStore.deleteStoredFile(transfer)
        }
        return expired.isNotEmpty()
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

    private fun String.decodeUrl(): String = URLDecoder.decode(this, "UTF-8")

    private fun String.headerSafe(): String = replace("\"", "'").replace("\r", "").replace("\n", "")

    companion object {
        const val ROOM_SERVER_READ_TIMEOUT_MILLIS = 70_000
        private const val CHAT_WEBSOCKET_PATH = "/ws/chat"
    }
}

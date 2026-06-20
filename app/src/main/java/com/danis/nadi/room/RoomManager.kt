package com.danis.nadi.room

import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.RoomStatus
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.security.IdentityValidator
import com.danis.nadi.security.TokenGenerator

class RoomManager(
    private val tokenGenerator: TokenGenerator = TokenGenerator(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val activeClientTimeoutMillis: Long = ACTIVE_CLIENT_TIMEOUT_MILLIS,
    private val maxMessages: Int = MAX_MESSAGES
) {
    private val lock = Any()
    private var session: RoomSession? = null
    private val clients = mutableListOf<ConnectedClient>()
    private val transfers = mutableListOf<TransferItem>()
    private val messages = mutableListOf<ChatMessage>()

    fun startPreparing(
        roomName: String,
        hostName: String,
        pin: String? = null
    ): RoomSession = synchronized(lock) {
        val cleanRoomName = roomName.trim().ifBlank { "Nadi Room" }
        val cleanHostName = hostName.trim().ifBlank { "Host Nadi" }
        val newSession = RoomSession(
            sessionId = tokenGenerator.newSessionId(),
            roomName = cleanRoomName,
            hostName = cleanHostName,
            pin = pin?.trim()?.takeIf { it.isNotBlank() },
            token = tokenGenerator.newToken(),
            startedAt = clock(),
            status = RoomStatus.PREPARING,
            localUrl = null,
            hotspotSsid = null
        )
        session = newSession
        clients.clear()
        transfers.clear()
        messages.clear()
        newSession
    }

    fun activate(localUrl: String, hotspotSsid: String? = null): RoomSession? = synchronized(lock) {
        val active = session?.copy(
            status = RoomStatus.ACTIVE,
            localUrl = localUrl,
            hotspotSsid = hotspotSsid
        )
        session = active
        active
    }

    fun fail(): RoomSession? = synchronized(lock) {
        val failed = session?.copy(status = RoomStatus.FAILED)
        session = failed
        failed
    }

    fun stopRoom(): RoomSession? = synchronized(lock) {
        val stopped = session?.copy(status = RoomStatus.STOPPED)
        session = stopped
        clients.clear()
        stopped
    }

    fun currentSession(): RoomSession? = synchronized(lock) {
        session
    }

    fun validateToken(token: String?): Boolean = synchronized(lock) {
        val current = session
        current != null && current.status == RoomStatus.ACTIVE && current.token == token
    }

    fun regenerateAccess(localUrlForToken: (String) -> String): RoomSession? = synchronized(lock) {
        val current = session ?: return@synchronized null
        if (current.status != RoomStatus.ACTIVE) return@synchronized null
        val nextToken = tokenGenerator.newToken()
        val refreshed = current.copy(
            token = nextToken,
            localUrl = localUrlForToken(nextToken)
        )
        session = refreshed
        clients.clear()
        refreshed
    }

    fun touchClient(
        displayName: String,
        userAgent: String,
        ipAddress: String
    ): ConnectedClient? = synchronized(lock) {
        val current = session ?: return@synchronized null
        if (current.status != RoomStatus.ACTIVE) return@synchronized null

        val now = clock()
        pruneStaleClients(now)
        val cleanName = displayName.trim().ifBlank { "Browser" }
        val existingIndex = clients.indexOfFirst { it.ipAddress == ipAddress && it.userAgent == userAgent }
        val client = if (existingIndex >= 0) {
            clients[existingIndex].copy(
                displayName = cleanName,
                lastSeenAt = now
            )
        } else {
            ConnectedClient(
                clientId = tokenGenerator.newSessionId(12),
                displayName = cleanName,
                joinedAt = now,
                lastSeenAt = now,
                userAgent = userAgent,
                ipAddress = ipAddress
            )
        }
        if (existingIndex >= 0) {
            clients[existingIndex] = client
        } else {
            clients.add(client)
        }
        client
    }

    fun touchIdentifiedClient(
        clientId: String,
        nim: String,
        name: String,
        userAgent: String,
        ipAddress: String
    ): ConnectedClient? = synchronized(lock) {
        val current = session ?: return@synchronized null
        if (current.status != RoomStatus.ACTIVE) return@synchronized null

        val identity = IdentityValidator.validate(nim, name) ?: return@synchronized null
        val now = clock()
        pruneStaleClients(now)
        val cleanClientId = clientId.cleanClientId().ifBlank { tokenGenerator.newSessionId(12) }
        val existingIndex = clients.indexOfFirst { it.clientId == cleanClientId }
        val client = if (existingIndex >= 0) {
            val existing = clients[existingIndex]
            existing.copy(
                lastSeenAt = now,
                userAgent = userAgent,
                ipAddress = ipAddress
            )
        } else {
            ConnectedClient(
                clientId = cleanClientId,
                displayName = identity.displayName,
                joinedAt = now,
                lastSeenAt = now,
                userAgent = userAgent,
                ipAddress = ipAddress,
                nim = identity.nim,
                name = identity.name
            )
        }
        if (existingIndex >= 0) {
            clients[existingIndex] = client
        } else {
            clients.add(client)
        }
        client
    }

    fun clientById(clientId: String?): ConnectedClient? = synchronized(lock) {
        val cleanClientId = clientId.orEmpty().cleanClientId()
        if (cleanClientId.isBlank()) return@synchronized null
        clients.firstOrNull { it.clientId == cleanClientId }
    }

    fun touchKnownClient(
        clientId: String?,
        userAgent: String,
        ipAddress: String
    ): ConnectedClient? = synchronized(lock) {
        val cleanClientId = clientId.orEmpty().cleanClientId()
        if (cleanClientId.isBlank()) return@synchronized null
        val now = clock()
        pruneStaleClients(now)
        val existingIndex = clients.indexOfFirst { it.clientId == cleanClientId }
        if (existingIndex < 0) return@synchronized null
        val client = clients[existingIndex].copy(
            lastSeenAt = now,
            userAgent = userAgent,
            ipAddress = ipAddress
        )
        clients[existingIndex] = client
        client
    }

    fun addTransfer(item: TransferItem): TransferItem = synchronized(lock) {
        transfers.removeAll { it.transferId == item.transferId }
        transfers.add(0, item)
        item
    }

    fun sharedFiles(): List<TransferItem> = synchronized(lock) {
        transfers.filter { it.direction == TransferDirection.SHARED }
    }

    fun receivedFiles(): List<TransferItem> = synchronized(lock) {
        transfers.filter { it.direction == TransferDirection.UPLOAD }
    }

    fun chatAttachments(): List<TransferItem> = synchronized(lock) {
        transfers.filter { it.direction == TransferDirection.CHAT_ATTACHMENT }
    }

    fun transferById(transferId: String): TransferItem? = synchronized(lock) {
        transfers.firstOrNull { it.transferId == transferId }
    }

    fun addMessage(
        senderId: String,
        senderName: String,
        text: String,
        attachment: TransferItem? = null
    ): ChatMessage? = synchronized(lock) {
        val cleanText = text.trim().take(1000)
        if (cleanText.isBlank() && attachment == null) return@synchronized null
        val message = ChatMessage(
            messageId = tokenGenerator.newSessionId(16),
            senderId = senderId.trim().ifBlank { "unknown" },
            senderName = senderName.trim().ifBlank { "Nadi" },
            text = cleanText.ifBlank { attachment?.fileName ?: "" },
            createdAt = clock(),
            status = "sent",
            attachmentTransferId = attachment?.transferId,
            attachmentFileName = attachment?.fileName,
            attachmentMimeType = attachment?.mimeType,
            attachmentSizeBytes = attachment?.sizeBytes ?: -1L
        )
        messages.add(message)
        trimMessages()
        message
    }

    fun messagesAfter(after: Long): List<ChatMessage> = synchronized(lock) {
        messages.filter { it.createdAt > after }.sortedBy { it.createdAt }
    }

    fun recentTransfers(limit: Int = 5): List<TransferItem> = synchronized(lock) {
        transfers.sortedByDescending { it.createdAt }.take(limit)
    }

    fun snapshot(): RoomSnapshot = synchronized(lock) {
        pruneStaleClients(clock())
        RoomSnapshot(
            session = session,
            clients = clients.toList(),
            transfers = transfers.toList(),
            messages = messages.toList()
        )
    }

    private fun pruneStaleClients(now: Long) {
        clients.removeAll { client ->
            now >= client.lastSeenAt && now - client.lastSeenAt > activeClientTimeoutMillis
        }
    }

    private fun trimMessages() {
        val overflow = messages.size - maxMessages.coerceAtLeast(0)
        if (overflow > 0) {
            messages.subList(0, overflow).clear()
        }
    }

    private fun String.cleanClientId(): String {
        return trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
    }

    private companion object {
        const val ACTIVE_CLIENT_TIMEOUT_MILLIS = 15_000L
        const val MAX_MESSAGES = 200
    }
}

data class RoomSnapshot(
    val session: RoomSession?,
    val clients: List<ConnectedClient>,
    val transfers: List<TransferItem>,
    val messages: List<ChatMessage>
)

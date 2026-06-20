package com.danis.nadi.room

import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.RoomStatus
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.security.TokenGenerator

class RoomManager(
    private val tokenGenerator: TokenGenerator = TokenGenerator(),
    private val clock: () -> Long = { System.currentTimeMillis() }
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

    fun transferById(transferId: String): TransferItem? = synchronized(lock) {
        transfers.firstOrNull { it.transferId == transferId }
    }

    fun addMessage(
        senderId: String,
        senderName: String,
        text: String
    ): ChatMessage? = synchronized(lock) {
        val cleanText = text.trim().take(1000)
        if (cleanText.isBlank()) return@synchronized null
        val message = ChatMessage(
            messageId = tokenGenerator.newSessionId(16),
            senderId = senderId.trim().ifBlank { "unknown" },
            senderName = senderName.trim().ifBlank { "Nadi" },
            text = cleanText,
            createdAt = clock(),
            status = "sent"
        )
        messages.add(message)
        message
    }

    fun messagesAfter(after: Long): List<ChatMessage> = synchronized(lock) {
        messages.filter { it.createdAt > after }.sortedBy { it.createdAt }
    }

    fun recentTransfers(limit: Int = 5): List<TransferItem> = synchronized(lock) {
        transfers.sortedByDescending { it.createdAt }.take(limit)
    }

    fun snapshot(): RoomSnapshot = synchronized(lock) {
        RoomSnapshot(
            session = session,
            clients = clients.toList(),
            transfers = transfers.toList(),
            messages = messages.toList()
        )
    }
}

data class RoomSnapshot(
    val session: RoomSession?,
    val clients: List<ConnectedClient>,
    val transfers: List<TransferItem>,
    val messages: List<ChatMessage>
)

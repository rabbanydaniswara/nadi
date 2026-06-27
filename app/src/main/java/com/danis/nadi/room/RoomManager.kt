package com.danis.nadi.room

import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.RoomStatus
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.security.IdentityValidator
import com.danis.nadi.security.PinValidator
import com.danis.nadi.security.TokenGenerator

class RoomManager(
    private val tokenGenerator: TokenGenerator = TokenGenerator(),
    private val pinValidator: PinValidator = PinValidator(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val activeClientTimeoutMillis: Long = ACTIVE_CLIENT_TIMEOUT_MILLIS,
    private val maxMessages: Int = MAX_MESSAGES
) {
    private val lock = Any()
    private var session: RoomSession? = null
    private val clients = mutableListOf<ConnectedClient>()
    private val identifiedClients = mutableMapOf<String, ConnectedClient>()
    private val transfers = mutableListOf<TransferItem>()
    private val messages = mutableListOf<ChatMessage>()
    private val messageListeners = mutableSetOf<(ChatMessage) -> Unit>()

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
            pin = pin?.trim()?.takeIf { PIN_PATTERN.matches(it) } ?: tokenGenerator.newPin(),
            token = tokenGenerator.newToken(),
            startedAt = clock(),
            status = RoomStatus.PREPARING,
            localUrl = null,
            hotspotSsid = null
        )
        session = newSession
        clients.clear()
        identifiedClients.clear()
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
        identifiedClients.clear()
        stopped
    }

    fun currentSession(): RoomSession? = synchronized(lock) {
        session
    }

    fun validateToken(token: String?): Boolean = synchronized(lock) {
        val current = session
        current != null && current.status == RoomStatus.ACTIVE && current.token == token
    }

    fun validateAccess(token: String?, pin: String?): Boolean = synchronized(lock) {
        val current = session
        if (current == null || current.status != RoomStatus.ACTIVE) return@synchronized false
        val tokenValid = current.token == token
        val pinValid = !current.pin.isNullOrBlank() && pinValidator.isConfiguredPinValid(current.pin, pin)
        tokenValid || pinValid
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
        identifiedClients.clear()
        refreshed
    }

    fun touchClient(
        displayName: String,
        userAgent: String,
        ipAddress: String
    ): ConnectedClient? = synchronized(lock) {
        if (session?.status != RoomStatus.ACTIVE) return@synchronized null

        val now = clock()
        pruneStaleClients(now)
        val cleanName = displayName.trim().ifBlank { "Browser" }
        val existingIndex = clients.indexOfFirst { it.ipAddress == ipAddress && it.userAgent == userAgent }
        
        return@synchronized if (existingIndex >= 0) {
            clients[existingIndex].copy(displayName = cleanName, lastSeenAt = now).also { clients[existingIndex] = it }
        } else {
            ConnectedClient(
                clientId = tokenGenerator.newSessionId(12),
                displayName = cleanName,
                joinedAt = now,
                lastSeenAt = now,
                userAgent = userAgent,
                ipAddress = ipAddress
            ).also { clients.add(it) }
        }
    }

    fun touchIdentifiedClient(
        clientId: String,
        nim: String,
        name: String,
        userAgent: String,
        ipAddress: String
    ): ConnectedClient? = synchronized(lock) {
        if (session?.status != RoomStatus.ACTIVE) return@synchronized null

        val identity = IdentityValidator.validate(nim, name) ?: return@synchronized null
        val now = clock()
        pruneStaleClients(now)
        val cleanClientId = clientId.cleanClientId().ifBlank { tokenGenerator.newSessionId(12) }
        val lockedIdentity = identifiedClients[cleanClientId]
            ?: clients.firstOrNull { it.clientId == cleanClientId && it.nim.isNotBlank() }
            
        return@synchronized (lockedIdentity?.copy(
            lastSeenAt = now,
            userAgent = userAgent,
            ipAddress = ipAddress
        ) ?: ConnectedClient(
            clientId = cleanClientId,
            displayName = identity.displayName,
            joinedAt = now,
            lastSeenAt = now,
            userAgent = userAgent,
            ipAddress = ipAddress,
            nim = identity.nim,
            name = identity.name
        )).also {
            identifiedClients[cleanClientId] = it
            upsertActiveClient(it)
        }
    }

    fun clientById(clientId: String?): ConnectedClient? = synchronized(lock) {
        val cleanClientId = clientId.orEmpty().cleanClientId()
        if (cleanClientId.isBlank()) return@synchronized null
        clients.firstOrNull { it.clientId == cleanClientId } ?: identifiedClients[cleanClientId]
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
        val knownClient = clients.firstOrNull { it.clientId == cleanClientId }
            ?: identifiedClients[cleanClientId]
            ?: return@synchronized null
        val client = knownClient.copy(
            lastSeenAt = now,
            userAgent = userAgent,
            ipAddress = ipAddress
        )
        if (client.nim.isNotBlank()) {
            identifiedClients[cleanClientId] = client
        }
        upsertActiveClient(client)
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

    fun chatAttachmentStorageStats(): ChatAttachmentStorageStats = synchronized(lock) {
        val attachments = transfers.filter { it.direction == TransferDirection.CHAT_ATTACHMENT }
        ChatAttachmentStorageStats(
            totalCount = attachments.size,
            availableCount = attachments.count { it.status == TransferStatus.SUCCESS || it.status == TransferStatus.DOWNLOADED },
            expiredCount = attachments.count { it.status == TransferStatus.EXPIRED },
            totalBytes = attachments
                .filter { it.status != TransferStatus.EXPIRED }
                .sumOf { it.sizeBytes.coerceAtLeast(0L) }
        )
    }

    fun transferById(transferId: String): TransferItem? = synchronized(lock) {
        transfers.firstOrNull { it.transferId == transferId }
    }

    fun markTransferDownloaded(transferId: String): TransferItem? = synchronized(lock) {
        updateTransferStatusLocked(transferId, TransferStatus.DOWNLOADED)
    }

    fun expireChatAttachmentsOlderThan(cutoffMillis: Long): List<TransferItem> = synchronized(lock) {
        val expired = transfers.filter {
            it.direction == TransferDirection.CHAT_ATTACHMENT &&
                it.status != TransferStatus.EXPIRED &&
                it.createdAt < cutoffMillis
        }
        expired.forEach { transfer ->
            updateTransferStatusLocked(transfer.transferId, TransferStatus.EXPIRED)
        }
        expired
    }

    fun expireAllChatAttachments(): List<TransferItem> = synchronized(lock) {
        val expired = transfers.filter {
            it.direction == TransferDirection.CHAT_ATTACHMENT && it.status != TransferStatus.EXPIRED
        }
        expired.forEach { transfer ->
            updateTransferStatusLocked(transfer.transferId, TransferStatus.EXPIRED)
        }
        expired
    }

    fun addMessageListener(listener: (ChatMessage) -> Unit): AutoCloseable = synchronized(lock) {
        messageListeners.add(listener)
        AutoCloseable {
            synchronized(lock) {
                messageListeners.remove(listener)
            }
        }
    }

    fun addMessage(
        senderId: String,
        senderName: String,
        text: String,
        attachment: TransferItem? = null
    ): ChatMessage? {
        val result = synchronized(lock) {
            val cleanText = text.trim().take(1000)
            if (cleanText.isBlank() && attachment == null) return@synchronized null
            val createdAt = maxOf(
                clock(),
                messages.lastOrNull()?.createdAt?.let { previous ->
                    if (previous == Long.MAX_VALUE) Long.MAX_VALUE else previous + 1
                } ?: Long.MIN_VALUE
            )
            val nextMessage = ChatMessage(
                messageId = tokenGenerator.newSessionId(16),
                senderId = senderId.trim().ifBlank { "unknown" },
                senderName = senderName.trim().ifBlank { "Nadi" },
                text = cleanText.ifBlank { attachment?.fileName ?: "" },
                createdAt = createdAt,
                status = "sent",
                attachmentTransferId = attachment?.transferId,
                attachmentFileName = attachment?.fileName,
                attachmentMimeType = attachment?.mimeType,
                attachmentSizeBytes = attachment?.sizeBytes ?: -1L,
                attachmentStatus = attachment?.status?.name?.lowercase().orEmpty()
            )
            messages.add(nextMessage)
            trimMessages()
            nextMessage to messageListeners.toList()
        } ?: return null
        val (message, listeners) = result
        listeners.forEach { listener ->
            runCatching { listener(message) }
        }
        return message
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

    private fun upsertActiveClient(client: ConnectedClient) {
        val existingIndex = clients.indexOfFirst { it.clientId == client.clientId }
        if (existingIndex >= 0) {
            clients[existingIndex] = client
        } else {
            clients.add(client)
        }
    }

    private fun trimMessages() {
        val overflow = messages.size - maxMessages.coerceAtLeast(0)
        if (overflow > 0) {
            messages.subList(0, overflow).clear()
        }
    }

    private fun updateTransferStatusLocked(transferId: String, status: TransferStatus): TransferItem? {
        val index = transfers.indexOfFirst { it.transferId == transferId }
        if (index < 0) return null
        val updated = transfers[index].copy(
            status = status,
            progress = if (status == TransferStatus.EXPIRED) 0 else transfers[index].progress,
            localUri = if (status == TransferStatus.EXPIRED) null else transfers[index].localUri
        )
        transfers[index] = updated
        messages.indices.forEach { messageIndex ->
            val message = messages[messageIndex]
            if (message.attachmentTransferId == transferId) {
                messages[messageIndex] = message.copy(attachmentStatus = status.name.lowercase())
            }
        }
        return updated
    }

    private fun String.cleanClientId(): String {
        return trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
    }

    private companion object {
        const val ACTIVE_CLIENT_TIMEOUT_MILLIS = 60_000L
        const val MAX_MESSAGES = 200
        val PIN_PATTERN = Regex("^\\d{4,8}$")
    }
}

data class RoomSnapshot(
    val session: RoomSession?,
    val clients: List<ConnectedClient>,
    val transfers: List<TransferItem>,
    val messages: List<ChatMessage>
)

data class ChatAttachmentStorageStats(
    val totalCount: Int,
    val availableCount: Int,
    val expiredCount: Int,
    val totalBytes: Long
)

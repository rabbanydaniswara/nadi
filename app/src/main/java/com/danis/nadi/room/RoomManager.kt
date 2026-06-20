package com.danis.nadi.room

import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.RoomStatus
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

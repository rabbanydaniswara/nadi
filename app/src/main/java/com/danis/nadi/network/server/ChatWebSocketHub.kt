package com.danis.nadi.network.server

import com.danis.nadi.model.ChatMessage
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame
import java.io.IOException

internal class ChatWebSocketHub(
    private val path: String,
    private val canOpenSession: (IHTTPSession) -> Boolean,
    private val touchSession: (IHTTPSession) -> Unit
) {
    private val lock = Any()
    private val sockets = mutableSetOf<ChatWebSocket>()

    fun open(handshake: IHTTPSession): NanoWSD.WebSocket {
        return ChatWebSocket(handshake)
    }

    fun broadcast(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val payload = NadiJson.chatMessagesPayload(messages)
        val snapshot = synchronized(lock) { sockets.toList() }
        val staleSockets = mutableListOf<ChatWebSocket>()
        snapshot.forEach { socket ->
            try {
                if (socket.isOpen && socket.canReceiveChat()) {
                    socket.send(payload)
                } else {
                    socket.closeAsUnauthorized()
                    staleSockets.add(socket)
                }
            } catch (_: IOException) {
                staleSockets.add(socket)
            }
        }
        if (staleSockets.isNotEmpty()) {
            synchronized(lock) {
                sockets.removeAll(staleSockets.toSet())
            }
        }
    }

    fun close() {
        val snapshot = synchronized(lock) {
            sockets.toList().also { sockets.clear() }
        }
        snapshot.forEach { socket ->
            socket.closeAsUnauthorized()
        }
    }

    private inner class ChatWebSocket(
        private val session: IHTTPSession
    ) : NanoWSD.WebSocket(session) {
        override fun onOpen() {
            if (!canReceiveChat()) {
                closeAsUnauthorized()
                return
            }
            synchronized(lock) {
                sockets.add(this)
            }
            runCatching { send("""{"type":"ready"}""") }
        }

        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            synchronized(lock) {
                sockets.remove(this)
            }
        }

        override fun onMessage(message: WebSocketFrame) {
            touchSession(session)
            if (message.textPayload == "ping") {
                runCatching { send("""{"type":"pong"}""") }
            }
        }

        override fun onPong(pong: WebSocketFrame) {
            touchSession(session)
        }

        override fun onException(exception: IOException) {
            synchronized(lock) {
                sockets.remove(this)
            }
        }

        fun canReceiveChat(): Boolean {
            return session.uri == path && canOpenSession(session)
        }

        fun closeAsUnauthorized() {
            try {
                close(WebSocketFrame.CloseCode.PolicyViolation, "identity_required", false)
            } catch (_: IOException) {
                synchronized(lock) {
                    sockets.remove(this)
                }
            }
        }
    }
}

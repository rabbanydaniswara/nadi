package com.danis.nadi.network.server

import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.RoomStatus
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NadiJsonTest {
    @Test
    fun roomSessionJsonEscapesUserControlledText() {
        val room = RoomSession(
            sessionId = "room-1",
            roomName = "Kelas \"A\"\nBaru",
            hostName = "Dosen\\Host",
            pin = "1234",
            token = "token",
            startedAt = 42L,
            status = RoomStatus.ACTIVE,
            localUrl = "http://127.0.0.1:8080/?token=a\tb",
            hotspotSsid = null
        )

        val json = NadiJson.roomSession(room, clientCount = 2, identityRequired = true)

        assertEquals(
            "{" +
                "\"sessionId\":\"room-1\"," +
                "\"roomName\":\"Kelas \\\"A\\\"\\nBaru\"," +
                "\"hostName\":\"Dosen\\\\Host\"," +
                "\"status\":\"active\"," +
                "\"localUrl\":\"http://127.0.0.1:8080/?token=a\\tb\"," +
                "\"clientCount\":2," +
                "\"identityRequired\":true," +
                "\"startedAt\":42" +
                "}",
            json
        )
    }

    @Test
    fun clientJsonIncludesStableIdentityFields() {
        val client = ConnectedClient(
            clientId = "client-1",
            displayName = "2310 - Danis",
            joinedAt = 10L,
            lastSeenAt = 20L,
            userAgent = "Browser",
            ipAddress = "127.0.0.1",
            nim = "2310",
            name = "Danis"
        )

        val json = NadiJson.client(client)

        assertTrue(json.contains("\"clientId\":\"client-1\""))
        assertTrue(json.contains("\"nim\":\"2310\""))
        assertTrue(json.contains("\"name\":\"Danis\""))
        assertTrue(json.contains("\"displayName\":\"2310 - Danis\""))
        assertFalse("Client JSON must not expose transport-only user agent", json.contains("userAgent"))
        assertFalse("Client JSON must not expose IP address", json.contains("ipAddress"))
    }

    @Test
    fun transferJsonUsesEmptyStringsForNullablePublicFields() {
        val transfer = TransferItem(
            transferId = "transfer-1",
            fileName = "materi.pdf",
            mimeType = null,
            sizeBytes = 2048L,
            direction = TransferDirection.CHAT_ATTACHMENT,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = 1000L,
            localUri = "file:///ignored",
            senderName = null
        )

        val json = NadiJson.transfer(transfer)

        assertEquals(
            "{" +
                "\"transferId\":\"transfer-1\"," +
                "\"fileName\":\"materi.pdf\"," +
                "\"mimeType\":\"\"," +
                "\"sizeBytes\":2048," +
                "\"direction\":\"chat_attachment\"," +
                "\"status\":\"success\"," +
                "\"progress\":100," +
                "\"createdAt\":1000," +
                "\"senderName\":\"\"" +
                "}",
            json
        )
    }

    @Test
    fun chatMessagesPayloadWrapsEscapedMessagesForWebSocketBroadcast() {
        val message = ChatMessage(
            messageId = "message-1",
            senderId = "client-1",
            senderName = "Danis",
            text = "Halo \"Nadi\"\nSiap",
            createdAt = 123L,
            status = "sent",
            attachmentTransferId = "transfer-1",
            attachmentFileName = "foto.png",
            attachmentMimeType = "image/png",
            attachmentSizeBytes = 512L,
            attachmentStatus = "success"
        )

        val payload = NadiJson.chatMessagesPayload(listOf(message))

        assertEquals(
            "{" +
                "\"type\":\"chat_messages\"," +
                "\"messages\":[{" +
                "\"messageId\":\"message-1\"," +
                "\"senderId\":\"client-1\"," +
                "\"senderName\":\"Danis\"," +
                "\"text\":\"Halo \\\"Nadi\\\"\\nSiap\"," +
                "\"createdAt\":123," +
                "\"status\":\"sent\"," +
                "\"attachmentTransferId\":\"transfer-1\"," +
                "\"attachmentFileName\":\"foto.png\"," +
                "\"attachmentMimeType\":\"image/png\"," +
                "\"attachmentSizeBytes\":512," +
                "\"attachmentStatus\":\"success\"" +
                "}]" +
                "}",
            payload
        )
    }
}

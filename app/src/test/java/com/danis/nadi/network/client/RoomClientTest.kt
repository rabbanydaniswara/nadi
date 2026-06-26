package com.danis.nadi.network.client

import com.danis.nadi.file.FilePayload
import com.danis.nadi.file.FileStore
import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.network.server.NadiHttpServer
import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RoomClientTest {
    private var server: NadiHttpServer? = null
    private var port: Int = 0
    private var roomManager = RoomManager()

    @Before
    fun setUp() {
        port = freePort()
        server = NadiHttpServer(port, roomManager, FakeFileStore()).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
        roomManager.startPreparing("Test Room", "Host User", "123456")
        roomManager.activate("http://127.0.0.1:$port")
    }

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun buildUrlAppendsTokenOrPinAndClientCorrectly() {
        val client = RoomClient(
            baseUrl = "http://127.0.0.1:$port",
            token = "my-token",
            pin = "123456",
            clientId = "my-client-id",
            clientName = "Client User",
            clientNim = "12345"
        )

        val urlWithToken = client.buildUrl("/api/test", appendClient = true)
        assertTrue(urlWithToken.contains("token=my-token"))
        assertTrue(urlWithToken.contains("clientId=my-client-id"))

        val clientWithPinOnly = RoomClient(
            baseUrl = "http://127.0.0.1:$port",
            token = null,
            pin = "123456",
            clientId = "my-client-id",
            clientName = "Client User",
            clientNim = "12345"
        )
        val urlWithPin = clientWithPinOnly.buildUrl("/api/test", appendClient = false)
        assertTrue(urlWithPin.contains("pin=123456"))
        assertFalse(urlWithPin.contains("clientId=my-client-id"))
    }

    @Test
    fun clientAuthenticatesSuccessfully() {
        val client = RoomClient(
            baseUrl = "http://127.0.0.1:$port",
            token = null,
            pin = "123456",
            clientId = "my-client-id",
            clientName = "Client User",
            clientNim = "12345"
        )

        val latch = CountDownLatch(1)
        var authSuccess = false
        var errMsg: String? = null

        client.authenticate { success, error ->
            authSuccess = success
            errMsg = error
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(authSuccess)
        assertNull(errMsg)
    }

    @Test
    fun clientSendsMessageSuccessfully() {
        val client = RoomClient(
            baseUrl = "http://127.0.0.1:$port",
            token = null,
            pin = "123456",
            clientId = "my-client-id",
            clientName = "Client User",
            clientNim = "12345"
        )

        // Pre-register client
        val latchAuth = CountDownLatch(1)
        client.authenticate { _, _ -> latchAuth.countDown() }
        latchAuth.await(3, TimeUnit.SECONDS)

        val latchMsg = CountDownLatch(1)
        var msgSuccess = false

        client.sendChatMessage("Hello from native client!") { success ->
            msgSuccess = success
            latchMsg.countDown()
        }

        assertTrue(latchMsg.await(5, TimeUnit.SECONDS))
        assertTrue(msgSuccess)

        // Verify message in RoomManager
        val messages = roomManager.messagesAfter(0L)
        assertEquals(1, messages.size)
        assertEquals("Hello from native client!", messages[0].text)
        assertEquals("12345 - Client User", messages[0].senderName)
    }

    @Test
    fun clientReceivesWebSocketMessages() {
        val client = RoomClient(
            baseUrl = "http://127.0.0.1:$port",
            token = null,
            pin = "123456",
            clientId = "my-client-id",
            clientName = "Client User",
            clientNim = "12345"
        )

        // Pre-register client
        val latchAuth = CountDownLatch(1)
        client.authenticate { _, _ -> latchAuth.countDown() }
        latchAuth.await(3, TimeUnit.SECONDS)

        val latchWs = CountDownLatch(1)
        var receivedMessage: ChatMessage? = null

        client.onMessageReceived = { msg ->
            receivedMessage = msg
            latchWs.countDown()
        }

        client.startWebSocket()

        // Wait a bit to ensure WebSocket is open and connected
        Thread.sleep(1000)

        // Send a message from host (adds it to RoomManager, which triggers broadcast via ChatWebSocketHub)
        roomManager.addMessage(
            senderId = "host",
            senderName = "Host User",
            text = "Hello from Host!"
        )

        assertTrue(latchWs.await(5, TimeUnit.SECONDS))
        assertNotNull(receivedMessage)
        assertEquals("Hello from Host!", receivedMessage?.text)
        assertEquals("Host User", receivedMessage?.senderName)
        
        client.close()
    }

    private fun freePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    private class FakeFileStore : FileStore {
        override fun openForDownload(transfer: TransferItem): FilePayload? = null
        override fun deleteStoredFile(transfer: TransferItem): Boolean = true
        override fun saveUpload(fileName: String, mimeType: String?, inputStream: InputStream): TransferItem {
            return TransferItem("", fileName, mimeType, 0L, TransferDirection.UPLOAD, TransferStatus.SUCCESS, 100, 0L, null, null)
        }
        override fun saveRoomFile(fileName: String, mimeType: String?, inputStream: InputStream, roomId: String?, folderName: String, direction: TransferDirection, senderName: String?): TransferItem {
            return TransferItem("", fileName, mimeType, 0L, direction, TransferStatus.SUCCESS, 100, 0L, null, null)
        }
    }
}

package com.danis.nadi.network.server

import com.danis.nadi.file.FilePayload
import com.danis.nadi.file.FileStore
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus
import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder

class NadiHttpServerTest {
    private var server: NadiHttpServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun healthEndpointReturnsRunningMessage() {
        val port = freePort()
        server = NadiHttpServer(port, RoomManager(), FakeFileStore()).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }

        val response = request("http://127.0.0.1:$port/health")

        assertEquals(200, response.code)
        assertTrue(response.body.contains("Nadi room server is running."))
    }

    @Test
    fun roomEndpointRequiresValidToken() {
        val scenario = startActiveRoom()

        val invalid = request("http://127.0.0.1:${scenario.port}/api/room?token=wrong")
        val valid = request("http://127.0.0.1:${scenario.port}/api/room?token=${scenario.token}")

        assertEquals(401, invalid.code)
        assertEquals(200, valid.code)
        assertTrue(valid.body.contains("Kelas Lokal"))
        assertTrue(valid.body.contains("active"))
    }

    @Test
    fun rootEndpointServesBrowserClientShell() {
        val scenario = startActiveRoom()

        val response = request("http://127.0.0.1:${scenario.port}/?token=${scenario.token}")

        assertEquals(200, response.code)
        assertTrue(response.body.contains("Terhubung ke Nadi"))
        assertTrue(response.body.contains("uploadProgress"))
    }

    @Test
    fun fileEndpointListsSharedFiles() {
        val scenario = startActiveRoom()
        scenario.manager.addTransfer(
            TransferItem(
                transferId = "file-1",
                fileName = "materi.pdf",
                mimeType = "application/pdf",
                sizeBytes = 2048,
                direction = TransferDirection.SHARED,
                status = TransferStatus.SUCCESS,
                progress = 100,
                createdAt = 1000,
                localUri = "memory://file-1",
                senderName = "Host"
            )
        )

        val response = request("http://127.0.0.1:${scenario.port}/api/files?token=${scenario.token}")

        assertEquals(200, response.code)
        assertTrue(response.body.contains("materi.pdf"))
        assertTrue(response.body.contains("file-1"))
    }

    @Test
    fun downloadEndpointStreamsSharedFile() {
        val scenario = startActiveRoom()
        scenario.manager.addTransfer(
            TransferItem(
                transferId = "file-1",
                fileName = "materi.txt",
                mimeType = "text/plain",
                sizeBytes = 4,
                direction = TransferDirection.SHARED,
                status = TransferStatus.SUCCESS,
                progress = 100,
                createdAt = 1000,
                localUri = "memory://file-1",
                senderName = "Host"
            )
        )

        val response = request("http://127.0.0.1:${scenario.port}/api/download/file-1?token=${scenario.token}")

        assertEquals(200, response.code)
        assertEquals("demo", response.body)
    }

    @Test
    fun uploadEndpointStoresReceivedFile() {
        val scenario = startActiveRoom()
        val boundary = "NadiBoundary"
        val body = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"upload.txt\"\r\n" +
            "Content-Type: text/plain\r\n\r\n" +
            "hello upload\r\n" +
            "--$boundary--\r\n"

        val response = request(
            url = "http://127.0.0.1:${scenario.port}/api/upload?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary"
        )

        assertEquals(200, response.code)
        assertTrue(response.body.contains("upload"))
        assertTrue(scenario.manager.receivedFiles().isNotEmpty())
    }

    @Test
    fun chatEndpointsSendAndListMessages() {
        val scenario = startActiveRoom()
        val body = "senderName=Browser&text=${URLEncoder.encode("Halo host", "UTF-8")}"

        val send = request(
            url = "http://127.0.0.1:${scenario.port}/api/chat?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded"
        )
        val list = request("http://127.0.0.1:${scenario.port}/api/chat?token=${scenario.token}&after=0")

        assertEquals(200, send.code)
        assertEquals(200, list.code)
        assertTrue(list.body.contains("Halo host"))
        assertTrue(list.body.contains("Browser"))
    }

    private fun startActiveRoom(): ServerScenario {
        val port = freePort()
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Kelas Lokal", hostName = "Danis")
        manager.activate("http://127.0.0.1:$port/?token=${session.token}")
        server = NadiHttpServer(port, manager, FakeFileStore()).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
        return ServerScenario(port, manager, session.token)
    }

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        contentType: String? = null
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        if (body != null) {
            connection.doOutput = true
            if (contentType != null) {
                connection.setRequestProperty("Content-Type", contentType)
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return HttpResponse(
            code = code,
            body = stream.bufferedReader().use { it.readText() }
        )
    }

    private fun freePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }
}

private class FakeFileStore : FileStore {
    override fun openForDownload(transfer: TransferItem): FilePayload {
        return FilePayload(
            inputStream = ByteArrayInputStream("demo".toByteArray()),
            fileName = transfer.fileName,
            mimeType = transfer.mimeType ?: "application/octet-stream",
            sizeBytes = 4
        )
    }

    override fun saveUpload(
        fileName: String,
        mimeType: String?,
        inputStream: InputStream
    ): TransferItem {
        return TransferItem(
            transferId = "uploaded",
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = inputStream.readBytes().size.toLong(),
            direction = TransferDirection.UPLOAD,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = 1000,
            localUri = "memory://uploaded",
            senderName = "Browser"
        )
    }
}

private data class ServerScenario(
    val port: Int,
    val manager: RoomManager,
    val token: String
)

private data class HttpResponse(
    val code: Int,
    val body: String
)

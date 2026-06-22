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
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    fun browserClientAssetsLoadFromExternalFiles() {
        val htmlAsset = browserClientAssetFile(BrowserClientAssets.HTML_FILE_NAME)
        val cssAsset = browserClientAssetFile(BrowserClientAssets.CSS_FILE_NAME)
        val jsAsset = browserClientAssetFile(BrowserClientAssets.JS_FILE_NAME)
        val html = BrowserClientAssets.html()
        val css = BrowserClientAssets.asset(BrowserClientAssets.CSS_FILE_NAME)?.content.orEmpty()
        val js = BrowserClientAssets.asset(BrowserClientAssets.JS_FILE_NAME)?.content.orEmpty()

        assertTrue("Browser client HTML asset file should exist at ${htmlAsset.path}", htmlAsset.isFile)
        assertTrue("Browser client CSS asset file should exist at ${cssAsset.path}", cssAsset.isFile)
        assertTrue("Browser client JS asset file should exist at ${jsAsset.path}", jsAsset.isFile)
        assertEquals(htmlAsset.readText(), html)
        assertEquals(cssAsset.readText(), css)
        assertEquals(jsAsset.readText(), js)
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("chatRealtimeStatus"))
        assertTrue(html.contains("chatStorageText"))
        assertTrue(html.contains("browser-client.css"))
        assertTrue(html.contains("browser-client.js"))
        assertTrue("Inline style block should be split into CSS asset", !html.contains("<style>"))
        assertTrue("Inline script block should be split into JS asset", !html.contains("<script>"))
        assertTrue(css.contains(".chat-window"))
        assertTrue(js.contains("new WebSocket"))
        assertTrue(js.contains("/ws/chat"))
        assertTrue(js.contains("chatKeepAliveIntervalMs = 3000"))
        assertTrue(js.contains("maxChatAttachmentBytes"))
        assertTrue(js.contains("Polling cadangan"))
        assertTrue("Kotlin interpolation escape should not leak into JS", !js.contains("\${'$'}{"))
    }

    @Test
    fun rootAndAssetEndpointsUseInjectedBrowserClientAssets() {
        val port = freePort()
        server = NadiHttpServer(
            port = port,
            roomManager = RoomManager(),
            fileStore = FakeFileStore(),
            browserClientHtml = { "<!doctype html><link rel=\"stylesheet\" href=\"/assets/browser-client.css\">" },
            browserClientAsset = { fileName ->
                when (fileName) {
                    BrowserClientAssets.CSS_FILE_NAME -> BrowserClientAsset(
                        fileName = fileName,
                        mimeType = "text/css; charset=utf-8",
                        content = "body { color: green; }"
                    )
                    BrowserClientAssets.JS_FILE_NAME -> BrowserClientAsset(
                        fileName = fileName,
                        mimeType = "application/javascript; charset=utf-8",
                        content = "window.nadiInjected = true;"
                    )
                    else -> null
                }
            }
        ).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }

        val root = request("http://127.0.0.1:$port/")
        val css = request("http://127.0.0.1:$port/assets/browser-client.css")
        val js = request("http://127.0.0.1:$port/assets/browser-client.js")
        val missing = request("http://127.0.0.1:$port/assets/unknown.js")

        assertEquals(200, root.code)
        assertEquals("<!doctype html><link rel=\"stylesheet\" href=\"/assets/browser-client.css\">", root.body)
        assertEquals(200, css.code)
        assertEquals("text/css; charset=utf-8", css.headers["Content-Type"])
        assertEquals("body { color: green; }", css.body)
        assertEquals(200, js.code)
        assertEquals("application/javascript; charset=utf-8", js.headers["Content-Type"])
        assertEquals("window.nadiInjected = true;", js.body)
        assertEquals(404, missing.code)
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
    fun roomAndIdentityEndpointsAcceptRoomPinAccess() {
        val scenario = startActiveRoom(registerClient = false, pin = "123456")
        val body = "clientId=client-42&nim=22010042&name=${URLEncoder.encode("Rabbany Daniswara", "UTF-8")}"

        val room = request("http://127.0.0.1:${scenario.port}/api/room?pin=${scenario.pin}")
        val identity = request(
            url = "http://127.0.0.1:${scenario.port}/api/identity?pin=${scenario.pin}",
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded"
        )

        assertEquals(200, room.code)
        assertEquals(200, identity.code)
        assertTrue(room.body.contains("Kelas Lokal"))
        assertEquals("22010042 - Rabbany Daniswara", scenario.manager.clientById("client-42")?.displayName)
    }

    @Test
    fun regeneratedTokenInvalidatesOldBrowserAccess() {
        val scenario = startActiveRoom()
        val oldToken = scenario.token
        val refreshed = scenario.manager.regenerateAccess { token ->
            "http://127.0.0.1:${scenario.port}/?token=$token"
        } ?: error("Expected refreshed session")

        val oldAccess = request("http://127.0.0.1:${scenario.port}/api/room?token=$oldToken")
        val newAccess = request("http://127.0.0.1:${scenario.port}/api/room?token=${refreshed.token}")

        assertEquals(401, oldAccess.code)
        assertEquals(200, newAccess.code)
    }

    @Test
    fun rootEndpointServesBrowserClientShell() {
        val scenario = startActiveRoom()

        val response = request("http://127.0.0.1:${scenario.port}/?token=${scenario.token}")

        assertEquals(200, response.code)
        assertTrue(response.body.contains("Terhubung ke Nadi"))
        assertTrue(response.body.contains("id=\"files\""))
        assertTrue(response.body.contains("id=\"messages\""))
        assertTrue(response.body.contains("uploadProgress"))
        assertTrue(response.body.contains("browser-client.css"))
        assertTrue(response.body.contains("browser-client.js"))
        assertTrue("Root shell should not contain inline JS after asset split", !response.body.contains("new WebSocket"))
    }

    @Test
    fun browserClientAssetEndpointsServeCssAndJavaScript() {
        val scenario = startActiveRoom()

        val css = request("http://127.0.0.1:${scenario.port}/assets/browser-client.css")
        val js = request("http://127.0.0.1:${scenario.port}/assets/browser-client.js")

        assertEquals(200, css.code)
        assertEquals("text/css; charset=utf-8", css.headers["Content-Type"])
        assertTrue(css.body.contains(".chat-window"))
        assertEquals(200, js.code)
        assertEquals("application/javascript; charset=utf-8", js.headers["Content-Type"])
        assertTrue(js.body.contains("downloadFile"))
        assertTrue(js.body.contains("/ws/chat"))
        assertTrue(js.body.contains("new WebSocket"))
        assertTrue(js.body.contains("chatKeepAliveIntervalMs = 3000"))
        assertTrue(js.body.contains("maxChatAttachmentBytes"))
        assertTrue(js.body.contains("Polling cadangan"))
    }

    @Test
    fun browserAndApiResponsesDisableCaching() {
        val scenario = startActiveRoom()

        val browser = request("http://127.0.0.1:${scenario.port}/?token=${scenario.token}")
        val api = request("http://127.0.0.1:${scenario.port}/api/room?token=${scenario.token}")

        assertEquals("no-store, max-age=0", browser.headers["Cache-Control"])
        assertEquals("no-cache", browser.headers["Pragma"])
        assertEquals("nosniff", browser.headers["X-Content-Type-Options"])
        assertEquals("no-store, max-age=0", api.headers["Cache-Control"])
        assertEquals("no-cache", api.headers["Pragma"])
        assertEquals("nosniff", api.headers["X-Content-Type-Options"])
    }

    @Test
    fun identityEndpointRegistersStudentIdentity() {
        val scenario = startActiveRoom(registerClient = false)
        val body = "clientId=client-42&nim=22010042&name=${URLEncoder.encode("Rabbany Daniswara", "UTF-8")}"

        val response = request(
            url = "http://127.0.0.1:${scenario.port}/api/identity?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded"
        )

        assertEquals(200, response.code)
        assertTrue(response.body.contains("22010042"))
        assertTrue(response.body.contains("Rabbany Daniswara"))
        assertEquals("22010042 - Rabbany Daniswara", scenario.manager.clientById("client-42")?.displayName)
    }

    @Test
    fun fileAndChatEndpointsRequireIdentity() {
        val scenario = startActiveRoom(registerClient = false)

        val files = request("http://127.0.0.1:${scenario.port}/api/files?token=${scenario.token}")
        val chat = request("http://127.0.0.1:${scenario.port}/api/chat?token=${scenario.token}&after=0")

        assertEquals(403, files.code)
        assertEquals(403, chat.code)
        assertTrue(files.body.contains("identity_required"))
        assertTrue(chat.body.contains("identity_required"))
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

        val response = request("http://127.0.0.1:${scenario.port}/api/files?${scenario.clientQuery}")

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

        val response = request("http://127.0.0.1:${scenario.port}/api/download/file-1?${scenario.clientQuery}")

        assertEquals(200, response.code)
        assertEquals("demo", response.body)
        assertEquals("no-store, max-age=0", response.headers["Cache-Control"])
        assertEquals("no-cache", response.headers["Pragma"])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
    }

    @Test
    fun uploadEndpointStoresReceivedFile() {
        val scenario = startActiveRoom()
        val boundary = "NadiBoundary"
        val body = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"clientId\"\r\n\r\n" +
            "${scenario.clientId}\r\n" +
            "--$boundary\r\n" +
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
        assertTrue(response.body.contains("22010001 - Rabbany Daniswara"))
        assertTrue(scenario.manager.receivedFiles().isNotEmpty())
    }

    @Test
    fun uploadEndpointRejectsOversizedFileRoomUpload() {
        val scenario = startActiveRoom(maxFileRoomUploadBytes = 8)
        val boundary = "NadiBoundary"
        val body = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"clientId\"\r\n\r\n" +
            "${scenario.clientId}\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"big.txt\"\r\n" +
            "Content-Type: text/plain\r\n\r\n" +
            "0123456789abcdef\r\n" +
            "--$boundary--\r\n"

        val response = request(
            url = "http://127.0.0.1:${scenario.port}/api/upload?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary"
        )

        assertEquals(400, response.code)
        assertTrue(response.body.contains("file_too_large"))
        assertTrue(scenario.manager.receivedFiles().isEmpty())
    }

    @Test
    fun chatEndpointsSendAndListMessages() {
        val scenario = startActiveRoom()
        val body = "clientId=${scenario.clientId}&text=${URLEncoder.encode("Halo host", "UTF-8")}"

        val send = request(
            url = "http://127.0.0.1:${scenario.port}/api/chat?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded"
        )
        val list = request("http://127.0.0.1:${scenario.port}/api/chat?${scenario.clientQuery}&after=0")

        assertEquals(200, send.code)
        assertEquals(200, list.code)
        assertTrue(list.body.contains("Halo host"))
        assertTrue(list.body.contains("22010001 - Rabbany Daniswara"))
    }

    @Test
    fun chatWebSocketReceivesPostedMessages() {
        val scenario = startActiveRoom()
        val received = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val socket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(
                URI.create("ws://127.0.0.1:${scenario.port}/ws/chat?${scenario.clientQuery}"),
                object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.request(1)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                        val text = data.toString()
                        received.add(text)
                        if (text.contains("Halo realtime")) {
                            latch.countDown()
                        }
                        webSocket.request(1)
                        return CompletableFuture.completedFuture(null)
                    }
                }
            )
            .get(3, TimeUnit.SECONDS)

        try {
            val body = "clientId=${scenario.clientId}&text=${URLEncoder.encode("Halo realtime", "UTF-8")}"
            val send = request(
                url = "http://127.0.0.1:${scenario.port}/api/chat?token=${scenario.token}",
                method = "POST",
                body = body,
                contentType = "application/x-www-form-urlencoded"
            )

            assertEquals(200, send.code)
            assertTrue("Expected websocket chat payload, got $received", latch.await(3, TimeUnit.SECONDS))
            assertTrue(received.any { it.contains("\"type\":\"chat_messages\"") })
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS)
        }
    }

    @Test
    fun chatWebSocketReceivesHostMessagesFromRoomManager() {
        val scenario = startActiveRoom()
        val received = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val socket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(
                URI.create("ws://127.0.0.1:${scenario.port}/ws/chat?${scenario.clientQuery}"),
                object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.request(1)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                        val text = data.toString()
                        received.add(text)
                        if (text.contains("Info dari host")) {
                            latch.countDown()
                        }
                        webSocket.request(1)
                        return CompletableFuture.completedFuture(null)
                    }
                }
            )
            .get(3, TimeUnit.SECONDS)

        try {
            scenario.manager.addMessage(
                senderId = "host",
                senderName = "Danis",
                text = "Info dari host"
            )

            assertTrue("Expected websocket host payload, got $received", latch.await(3, TimeUnit.SECONDS))
            assertTrue(received.any { it.contains("\"type\":\"chat_messages\"") })
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS)
        }
    }

    @Test
    fun chatWebSocketStaysOpenPastDefaultReadTimeoutWhenIdle() {
        val scenario = startActiveRoom()
        val closeLatch = CountDownLatch(1)
        val messageLatch = CountDownLatch(1)
        val socket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(
                URI.create("ws://127.0.0.1:${scenario.port}/ws/chat?${scenario.clientQuery}"),
                object : WebSocket.Listener {
                    override fun onOpen(webSocket: WebSocket) {
                        webSocket.request(1)
                    }

                    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
                        if (data.toString().contains("Setelah idle")) {
                            messageLatch.countDown()
                        }
                        webSocket.request(1)
                        return CompletableFuture.completedFuture(null)
                    }

                    override fun onClose(
                        webSocket: WebSocket,
                        statusCode: Int,
                        reason: String
                    ): CompletionStage<*> {
                        closeLatch.countDown()
                        return CompletableFuture.completedFuture(null)
                    }
                }
            )
            .get(3, TimeUnit.SECONDS)

        try {
            assertTrue(
                "Test setup expects the default NanoHTTPD read timeout to be shorter than the room server timeout",
                NanoHTTPD.SOCKET_READ_TIMEOUT < NadiHttpServer.ROOM_SERVER_READ_TIMEOUT_MILLIS
            )
            Thread.sleep(NanoHTTPD.SOCKET_READ_TIMEOUT + 1_000L)
            assertEquals("WebSocket closed while idle", 1L, closeLatch.count)

            scenario.manager.addMessage(
                senderId = "host",
                senderName = "Danis",
                text = "Setelah idle"
            )

            assertTrue("Expected websocket payload after idle period", messageLatch.await(3, TimeUnit.SECONDS))
            assertEquals("WebSocket closed after idle message", 1L, closeLatch.count)
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(3, TimeUnit.SECONDS)
        }
    }

    @Test
    fun protectedEndpointsRestoreKnownIdentityAfterPresenceTimeout() {
        var now = 1000L
        val scenario = startActiveRoom(
            clock = { now },
            activeClientTimeoutMillis = 5_000L
        )
        now = 7_000L

        assertEquals(0, scenario.manager.snapshot().clients.size)

        val chat = request("http://127.0.0.1:${scenario.port}/api/chat?${scenario.clientQuery}&after=0")
        val body = "clientId=${scenario.clientId}&text=${URLEncoder.encode("Masih masuk", "UTF-8")}"
        val send = request(
            url = "http://127.0.0.1:${scenario.port}/api/chat?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "application/x-www-form-urlencoded"
        )

        assertEquals(200, chat.code)
        assertEquals(200, send.code)
        assertTrue(send.body.contains("22010001 - Rabbany Daniswara"))
        assertEquals(1, scenario.manager.snapshot().clients.size)
    }

    @Test
    fun chatAttachmentEndpointStoresAttachmentOutsideFileRoom() {
        val scenario = startActiveRoom()
        val boundary = "NadiBoundary"
        val body = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"clientId\"\r\n\r\n" +
            "${scenario.clientId}\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"text\"\r\n\r\n" +
            "Ini lampiran tugas\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"catatan.pdf\"\r\n" +
            "Content-Type: application/pdf\r\n\r\n" +
            "pdf data\r\n" +
            "--$boundary--\r\n"

        val send = request(
            url = "http://127.0.0.1:${scenario.port}/api/chat-attachment?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary"
        )
        val fileRoom = request("http://127.0.0.1:${scenario.port}/api/files?${scenario.clientQuery}")
        val chat = request("http://127.0.0.1:${scenario.port}/api/chat?${scenario.clientQuery}&after=0")

        assertEquals(200, send.code)
        assertTrue(send.body.contains("catatan.pdf"))
        assertTrue(send.body.contains("chat_attachment"))
        assertTrue(send.body.contains("\"attachmentStatus\":\"success\""))
        assertTrue(fileRoom.body.contains("\"files\":[]"))
        assertTrue(chat.body.contains("attachmentTransferId"))
        assertTrue(chat.body.contains("\"attachmentStatus\":\"success\""))
        assertEquals(1, scenario.manager.chatAttachments().size)
        assertTrue(scenario.manager.receivedFiles().isEmpty())
    }

    @Test
    fun chatAttachmentEndpointRejectsRoomStorageOverflow() {
        val scenario = startActiveRoom(maxChatAttachmentStorageBytes = 12)
        val boundary = "NadiBoundary"
        val body = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"clientId\"\r\n\r\n" +
            "${scenario.clientId}\r\n" +
            "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"catatan.pdf\"\r\n" +
            "Content-Type: application/pdf\r\n\r\n" +
            "0123456789abcdef\r\n" +
            "--$boundary--\r\n"

        val send = request(
            url = "http://127.0.0.1:${scenario.port}/api/chat-attachment?token=${scenario.token}",
            method = "POST",
            body = body,
            contentType = "multipart/form-data; boundary=$boundary"
        )

        assertEquals(400, send.code)
        assertTrue(send.body.contains("chat_attachment_storage_full"))
        assertTrue(scenario.manager.chatAttachments().isEmpty())
    }

    @Test
    fun expiredChatAttachmentIsMarkedUnavailableAndCannotDownload() {
        var now = 1_000L
        val scenario = startActiveRoom(clock = { now })
        val attachment = TransferItem(
            transferId = "old-chat-file",
            fileName = "lama.pdf",
            mimeType = "application/pdf",
            sizeBytes = 4,
            direction = TransferDirection.CHAT_ATTACHMENT,
            status = TransferStatus.SUCCESS,
            progress = 100,
            createdAt = now,
            localUri = "memory://old-chat-file",
            senderName = "Browser"
        )
        scenario.manager.addTransfer(attachment)
        scenario.manager.addMessage(
            senderId = scenario.clientId,
            senderName = "22010001 - Rabbany Daniswara",
            text = "Lampiran lama",
            attachment = attachment
        )
        now += ServerFileRules.CHAT_ATTACHMENT_TTL_MILLIS + 1

        val room = request("http://127.0.0.1:${scenario.port}/api/room?${scenario.clientQuery}")
        val chat = request("http://127.0.0.1:${scenario.port}/api/chat?${scenario.clientQuery}&after=0")
        val download = request("http://127.0.0.1:${scenario.port}/api/download/old-chat-file?${scenario.clientQuery}")

        assertEquals(200, room.code)
        assertTrue(room.body.contains("\"expiredCount\":1"))
        assertTrue(chat.body.contains("\"attachmentStatus\":\"expired\""))
        assertEquals(410, download.code)
        assertTrue(download.body.contains("file_expired"))
    }

    private fun startActiveRoom(
        registerClient: Boolean = true,
        pin: String? = null,
        maxFileRoomUploadBytes: Long = 100L * 1024L * 1024L,
        maxChatAttachmentStorageBytes: Long = ServerFileRules.MAX_CHAT_ATTACHMENT_STORAGE_BYTES,
        clock: () -> Long = { 1000L },
        activeClientTimeoutMillis: Long? = null
    ): ServerScenario {
        val port = freePort()
        val manager = activeClientTimeoutMillis?.let { timeout ->
            RoomManager(clock = clock, activeClientTimeoutMillis = timeout)
        } ?: RoomManager(clock = clock)
        val session = manager.startPreparing(roomName = "Kelas Lokal", hostName = "Danis", pin = pin)
        manager.activate("http://127.0.0.1:$port/?token=${session.token}")
        val clientId = "client-1"
        if (registerClient) {
            manager.touchIdentifiedClient(
                clientId = clientId,
                nim = "22010001",
                name = "Rabbany Daniswara",
                userAgent = "JUnit Browser",
                ipAddress = "127.0.0.1"
            )
        }
        server = NadiHttpServer(
            port = port,
            roomManager = manager,
            fileStore = FakeFileStore(),
            maxFileRoomUploadBytes = maxFileRoomUploadBytes,
            maxChatAttachmentStorageBytes = maxChatAttachmentStorageBytes,
            clock = clock
        ).also {
            it.start(NadiHttpServer.ROOM_SERVER_READ_TIMEOUT_MILLIS, false)
        }
        return ServerScenario(port, manager, session.token, session.pin.orEmpty(), clientId)
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
            headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.orEmpty() }
                .mapValues { it.value.firstOrNull().orEmpty() },
            body = stream.bufferedReader().use { it.readText() }
        )
    }

    private fun freePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    private fun browserClientAssetFile(fileName: String): File {
        return listOf(
            File("app/src/main/assets/$fileName"),
            File("src/main/assets/$fileName")
        ).first { it.isFile }
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
    val token: String,
    val pin: String,
    val clientId: String
) {
    val clientQuery: String
        get() = "token=$token&clientId=$clientId"
}

private data class HttpResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String
)

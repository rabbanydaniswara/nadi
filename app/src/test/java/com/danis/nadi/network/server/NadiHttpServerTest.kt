package com.danis.nadi.network.server

import com.danis.nadi.room.RoomManager
import fi.iki.elonen.NanoHTTPD
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

class NadiHttpServerTest {
    private var server: NadiHttpServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun healthEndpointReturnsRunningMessage() {
        val port = freePort()
        server = NadiHttpServer(port, RoomManager()).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }

        val response = request("http://127.0.0.1:$port/health")

        assertEquals(200, response.code)
        assertTrue(response.body.contains("Nadi room server is running."))
    }

    @Test
    fun roomEndpointRequiresValidToken() {
        val port = freePort()
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Kelas Lokal", hostName = "Danis")
        manager.activate("http://127.0.0.1:$port/?token=${session.token}")
        server = NadiHttpServer(port, manager).also {
            it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }

        val invalid = request("http://127.0.0.1:$port/api/room?token=wrong")
        val valid = request("http://127.0.0.1:$port/api/room?token=${session.token}")

        assertEquals(401, invalid.code)
        assertEquals(200, valid.code)
        assertTrue(valid.body.contains("Kelas Lokal"))
        assertTrue(valid.body.contains("active"))
    }

    private fun request(url: String): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
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

private data class HttpResponse(
    val code: Int,
    val body: String
)

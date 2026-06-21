package com.danis.nadi.room

import com.danis.nadi.model.RoomStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomManagerTest {
    @Test
    fun startPreparingCreatesPreparingSession() {
        val manager = RoomManager(clock = { 1000L })

        val session = manager.startPreparing(roomName = "Kelas A", hostName = "Danis")

        assertEquals("Kelas A", session.roomName)
        assertEquals("Danis", session.hostName)
        assertEquals(RoomStatus.PREPARING, session.status)
        assertEquals(1000L, session.startedAt)
        assertEquals(6, session.pin?.length)
        assertTrue(session.pin.orEmpty().all { it.isDigit() })
        assertNull(session.localUrl)
    }

    @Test
    fun activateMakesTokenValid() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")

        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        assertTrue(manager.validateToken(session.token))
        assertFalse(manager.validateToken("wrong-token"))
    }

    @Test
    fun validateAccessAcceptsTokenOrRoomPin() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host", pin = "123456")

        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        assertTrue(manager.validateAccess(token = session.token, pin = null))
        assertTrue(manager.validateAccess(token = null, pin = "123456"))
        assertFalse(manager.validateAccess(token = "wrong-token", pin = "000000"))
    }

    @Test
    fun stopRoomIsIdempotentAndInvalidatesToken() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        manager.stopRoom()
        val stoppedAgain = manager.stopRoom()

        assertEquals(RoomStatus.STOPPED, stoppedAgain?.status)
        assertFalse(manager.validateToken(session.token))
    }

    @Test
    fun regenerateAccessInvalidatesPreviousToken() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        val refreshed = manager.regenerateAccess { token ->
            "http://127.0.0.1:8080/?token=$token"
        }

        assertFalse(manager.validateToken(session.token))
        assertTrue(manager.validateToken(refreshed?.token))
        assertTrue(refreshed?.localUrl?.contains(refreshed.token) == true)
    }

    @Test
    fun snapshotOnlyCountsRecentlySeenClients() {
        var now = 1000L
        val manager = RoomManager(
            clock = { now },
            activeClientTimeoutMillis = 5_000L
        )
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        manager.touchClient(
            displayName = "Browser",
            userAgent = "Firefox",
            ipAddress = "192.168.1.8"
        )
        now = 5_000L
        manager.touchClient(
            displayName = "Laptop",
            userAgent = "Chrome",
            ipAddress = "192.168.1.9"
        )
        now = 6_000L

        assertEquals(2, manager.snapshot().clients.size)

        now = 10_000L

        val activeClients = manager.snapshot().clients

        assertEquals(1, activeClients.size)
        assertEquals("Laptop", activeClients.single().displayName)
    }

    @Test
    fun identifiedClientKeepsLockedIdentity() {
        var now = 1000L
        val manager = RoomManager(clock = { now })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        val joined = manager.touchIdentifiedClient(
            clientId = "client-1",
            nim = "22010001",
            name = "Rabbany Daniswara",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )
        now = 2000L
        val changed = manager.touchIdentifiedClient(
            clientId = "client-1",
            nim = "99999999",
            name = "Nama Lain",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )

        assertEquals("22010001", joined?.nim)
        assertEquals("Rabbany Daniswara", joined?.name)
        assertEquals("22010001", changed?.nim)
        assertEquals("Rabbany Daniswara", changed?.name)
        assertEquals("22010001 - Rabbany Daniswara", manager.clientById("client-1")?.displayName)
    }

    @Test
    fun knownClientTouchKeepsIdentityActive() {
        var now = 1000L
        val manager = RoomManager(
            clock = { now },
            activeClientTimeoutMillis = 5_000L
        )
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")
        manager.touchIdentifiedClient(
            clientId = "client-1",
            nim = "22010001",
            name = "Rabbany Daniswara",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )

        now = 5_000L
        manager.touchKnownClient(
            clientId = "client-1",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )
        now = 9_500L

        assertEquals(1, manager.snapshot().clients.size)
        assertEquals("22010001", manager.snapshot().clients.single().nim)
    }

    @Test
    fun knownClientTouchRestoresStaleLockedIdentity() {
        var now = 1000L
        val manager = RoomManager(
            clock = { now },
            activeClientTimeoutMillis = 5_000L
        )
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")
        manager.touchIdentifiedClient(
            clientId = "client-1",
            nim = "22010001",
            name = "Rabbany Daniswara",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )

        now = 7_000L

        assertEquals(0, manager.snapshot().clients.size)
        assertEquals("22010001 - Rabbany Daniswara", manager.clientById("client-1")?.displayName)

        val restored = manager.touchKnownClient(
            clientId = "client-1",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )

        assertEquals("22010001", restored?.nim)
        assertEquals(1, manager.snapshot().clients.size)
        assertEquals("22010001 - Rabbany Daniswara", manager.snapshot().clients.single().displayName)
    }

    @Test
    fun regenerateAccessClearsLockedIdentities() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")
        manager.touchIdentifiedClient(
            clientId = "client-1",
            nim = "22010001",
            name = "Rabbany Daniswara",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )

        manager.regenerateAccess { token -> "http://127.0.0.1:8080/?token=$token" }

        assertNull(manager.clientById("client-1"))
        assertNull(
            manager.touchKnownClient(
                clientId = "client-1",
                userAgent = "Chrome",
                ipAddress = "192.168.1.8"
            )
        )
    }

    @Test
    fun invalidIdentifiedClientIsRejected() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        val client = manager.touchIdentifiedClient(
            clientId = "client-1",
            nim = "x",
            name = "A",
            userAgent = "Chrome",
            ipAddress = "192.168.1.8"
        )

        assertNull(client)
        assertEquals(0, manager.snapshot().clients.size)
    }

    @Test
    fun chatRetentionKeepsNewestMessagesOnly() {
        var now = 1000L
        val manager = RoomManager(
            clock = { now },
            maxMessages = 3
        )
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        listOf("satu", "dua", "tiga", "empat").forEach { text ->
            now += 1
            manager.addMessage(
                senderId = "browser",
                senderName = "Browser",
                text = text
            )
        }

        val messages = manager.messagesAfter(0)

        assertEquals(listOf("dua", "tiga", "empat"), messages.map { it.text })
    }
}

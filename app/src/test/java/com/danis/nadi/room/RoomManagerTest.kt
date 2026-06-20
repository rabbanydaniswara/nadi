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
    fun stopRoomIsIdempotentAndInvalidatesToken() {
        val manager = RoomManager(clock = { 1000L })
        val session = manager.startPreparing(roomName = "Nadi Room", hostName = "Host")
        manager.activate("http://127.0.0.1:8080/?token=${session.token}")

        manager.stopRoom()
        val stoppedAgain = manager.stopRoom()

        assertEquals(RoomStatus.STOPPED, stoppedAgain?.status)
        assertFalse(manager.validateToken(session.token))
    }
}

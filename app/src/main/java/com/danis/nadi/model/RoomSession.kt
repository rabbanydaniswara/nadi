package com.danis.nadi.model

data class RoomSession(
    val sessionId: String,
    val roomName: String,
    val hostName: String,
    val pin: String?,
    val token: String,
    val startedAt: Long,
    val status: RoomStatus,
    val localUrl: String?,
    val hotspotSsid: String?
)

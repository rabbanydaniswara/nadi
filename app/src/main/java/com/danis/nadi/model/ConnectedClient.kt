package com.danis.nadi.model

data class ConnectedClient(
    val clientId: String,
    val displayName: String,
    val joinedAt: Long,
    val lastSeenAt: Long,
    val userAgent: String,
    val ipAddress: String
)

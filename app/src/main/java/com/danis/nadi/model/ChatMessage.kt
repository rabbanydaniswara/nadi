package com.danis.nadi.model

data class ChatMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val createdAt: Long,
    val status: String
)

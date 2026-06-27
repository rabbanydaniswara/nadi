package com.danis.nadi.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.danis.nadi.model.ChatMessage

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val createdAt: Long,
    val status: String,
    val attachmentTransferId: String? = null,
    val attachmentFileName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentSizeBytes: Long = -1L,
    val attachmentStatus: String = ""
) {
    fun toDomain(): ChatMessage {
        return ChatMessage(
            messageId = messageId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            createdAt = createdAt,
            status = status,
            attachmentTransferId = attachmentTransferId,
            attachmentFileName = attachmentFileName,
            attachmentMimeType = attachmentMimeType,
            attachmentSizeBytes = attachmentSizeBytes,
            attachmentStatus = attachmentStatus
        )
    }

    companion object {
        fun fromDomain(domain: ChatMessage, roomId: String): ChatMessageEntity {
            return ChatMessageEntity(
                messageId = domain.messageId,
                roomId = roomId,
                senderId = domain.senderId,
                senderName = domain.senderName,
                text = domain.text,
                createdAt = domain.createdAt,
                status = domain.status,
                attachmentTransferId = domain.attachmentTransferId,
                attachmentFileName = domain.attachmentFileName,
                attachmentMimeType = domain.attachmentMimeType,
                attachmentSizeBytes = domain.attachmentSizeBytes,
                attachmentStatus = domain.attachmentStatus
            )
        }
    }
}

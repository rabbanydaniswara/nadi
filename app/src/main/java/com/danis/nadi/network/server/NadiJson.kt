package com.danis.nadi.network.server

import com.danis.nadi.model.ChatMessage
import com.danis.nadi.model.ConnectedClient
import com.danis.nadi.model.RoomSession
import com.danis.nadi.model.TransferItem
import com.danis.nadi.room.ChatAttachmentStorageStats

internal object NadiJson {
    fun roomSession(
        room: RoomSession,
        clientCount: Int,
        identityRequired: Boolean,
        chatAttachmentStorageStats: ChatAttachmentStorageStats? = null,
        maxChatAttachmentStorageBytes: Long = 0L
    ): String {
        return buildString {
            append("{")
            append("\"sessionId\":\"").append(room.sessionId.escapeJson()).append("\",")
            append("\"roomName\":\"").append(room.roomName.escapeJson()).append("\",")
            append("\"hostName\":\"").append(room.hostName.escapeJson()).append("\",")
            append("\"status\":\"").append(room.status.name.lowercase()).append("\",")
            append("\"localUrl\":\"").append((room.localUrl ?: "").escapeJson()).append("\",")
            append("\"clientCount\":").append(clientCount).append(",")
            append("\"identityRequired\":").append(identityRequired).append(",")
            if (chatAttachmentStorageStats != null) {
                append("\"chatAttachmentStorage\":").append(chatAttachmentStorage(chatAttachmentStorageStats, maxChatAttachmentStorageBytes)).append(",")
            }
            append("\"startedAt\":").append(room.startedAt)
            append("}")
        }
    }

    fun client(client: ConnectedClient): String {
        return buildString {
            append("{")
            append("\"clientId\":\"").append(client.clientId.escapeJson()).append("\",")
            append("\"nim\":\"").append(client.nim.escapeJson()).append("\",")
            append("\"name\":\"").append(client.name.escapeJson()).append("\",")
            append("\"displayName\":\"").append(client.displayName.escapeJson()).append("\",")
            append("\"joinedAt\":").append(client.joinedAt).append(",")
            append("\"lastSeenAt\":").append(client.lastSeenAt)
            append("}")
        }
    }

    fun transferArray(items: List<TransferItem>): String = items.joinToString(prefix = "[", postfix = "]") { transfer(it) }

    fun transfer(item: TransferItem): String {
        return buildString {
            append("{")
            append("\"transferId\":\"").append(item.transferId.escapeJson()).append("\",")
            append("\"fileName\":\"").append(item.fileName.escapeJson()).append("\",")
            append("\"mimeType\":\"").append((item.mimeType ?: "").escapeJson()).append("\",")
            append("\"sizeBytes\":").append(item.sizeBytes).append(",")
            append("\"direction\":\"").append(item.direction.name.lowercase()).append("\",")
            append("\"status\":\"").append(item.status.name.lowercase()).append("\",")
            append("\"progress\":").append(item.progress).append(",")
            append("\"createdAt\":").append(item.createdAt).append(",")
            append("\"senderName\":\"").append((item.senderName ?: "").escapeJson()).append("\"")
            append("}")
        }
    }

    fun messageArray(messages: List<ChatMessage>): String = messages.joinToString(prefix = "[", postfix = "]") { message(it) }

    fun message(message: ChatMessage): String {
        return buildString {
            append("{")
            append("\"messageId\":\"").append(message.messageId.escapeJson()).append("\",")
            append("\"senderId\":\"").append(message.senderId.escapeJson()).append("\",")
            append("\"senderName\":\"").append(message.senderName.escapeJson()).append("\",")
            append("\"text\":\"").append(message.text.escapeJson()).append("\",")
            append("\"createdAt\":").append(message.createdAt).append(",")
            append("\"status\":\"").append(message.status.escapeJson()).append("\",")
            append("\"attachmentTransferId\":\"").append((message.attachmentTransferId ?: "").escapeJson()).append("\",")
            append("\"attachmentFileName\":\"").append((message.attachmentFileName ?: "").escapeJson()).append("\",")
            append("\"attachmentMimeType\":\"").append((message.attachmentMimeType ?: "").escapeJson()).append("\",")
            append("\"attachmentSizeBytes\":").append(message.attachmentSizeBytes).append(",")
            append("\"attachmentStatus\":\"").append(message.attachmentStatus.escapeJson()).append("\"")
            append("}")
        }
    }

    fun chatMessagesPayload(messages: List<ChatMessage>): String {
        return """{"type":"chat_messages","messages":${messageArray(messages)}}"""
    }

    fun String.escapeJson(): String {
        return buildString(length) {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }

    private fun chatAttachmentStorage(stats: ChatAttachmentStorageStats, maxBytes: Long): String {
        return buildString {
            append("{")
            append("\"totalCount\":").append(stats.totalCount).append(",")
            append("\"availableCount\":").append(stats.availableCount).append(",")
            append("\"expiredCount\":").append(stats.expiredCount).append(",")
            append("\"totalBytes\":").append(stats.totalBytes).append(",")
            append("\"maxBytes\":").append(maxBytes)
            append("}")
        }
    }
}

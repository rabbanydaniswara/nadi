package com.danis.nadi.data.repository

import com.danis.nadi.data.db.dao.ChatMessageDao
import com.danis.nadi.data.db.entity.ChatMessageEntity
import com.danis.nadi.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    fun getMessagesForRoom(roomId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForRoom(roomId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessagesForRoomOnce(roomId: String): List<ChatMessage> {
        return chatMessageDao.getMessagesForRoomOnce(roomId).map { it.toDomain() }
    }

    suspend fun saveMessage(roomId: String, message: ChatMessage) {
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(message, roomId))
    }

    suspend fun saveMessages(roomId: String, messages: List<ChatMessage>) {
        chatMessageDao.insertMessages(messages.map { ChatMessageEntity.fromDomain(it, roomId) })
    }

    suspend fun clearMessagesForRoom(roomId: String) {
        chatMessageDao.deleteMessagesForRoom(roomId)
    }
}

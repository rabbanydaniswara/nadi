package com.danis.nadi.data.repository

import androidx.lifecycle.asFlow
import com.danis.nadi.data.db.dao.ChatMessageDao
import com.danis.nadi.data.db.entity.ChatMessageEntity
import com.danis.nadi.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    fun getMessagesForRoom(roomId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForRoom(roomId).asFlow().map { entities ->
            entities?.map { it.toDomain() } ?: emptyList()
        }
    }

    suspend fun getMessagesForRoomOnce(roomId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        chatMessageDao.getMessagesForRoomOnce(roomId)?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun saveMessage(roomId: String, message: ChatMessage) = withContext(Dispatchers.IO) {
        chatMessageDao.insertMessage(ChatMessageEntity.fromDomain(message, roomId))
    }

    suspend fun saveMessages(roomId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        chatMessageDao.insertMessages(messages.map { ChatMessageEntity.fromDomain(it, roomId) })
    }

    suspend fun clearMessagesForRoom(roomId: String) = withContext(Dispatchers.IO) {
        chatMessageDao.deleteMessagesForRoom(roomId)
    }
}

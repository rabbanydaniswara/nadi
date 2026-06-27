package com.danis.nadi.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danis.nadi.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    fun getMessagesForRoom(roomId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    suspend fun getMessagesForRoomOnce(roomId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)
}

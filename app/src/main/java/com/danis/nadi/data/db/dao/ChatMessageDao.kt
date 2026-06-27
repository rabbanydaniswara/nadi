package com.danis.nadi.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danis.nadi.data.db.entity.ChatMessageEntity

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    fun getMessagesForRoom(roomId: String): LiveData<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    fun getMessagesForRoomOnce(roomId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    fun deleteMessagesForRoom(roomId: String)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    fun deleteMessageById(messageId: String)
}

package com.danis.nadi.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.danis.nadi.data.db.entity.ChatMessageEntity;
import java.util.List;

@Dao
public interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    LiveData<List<ChatMessageEntity>> getMessagesForRoom(String roomId);

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY createdAt ASC")
    List<ChatMessageEntity> getMessagesForRoomOnce(String roomId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(ChatMessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessages(List<ChatMessageEntity> messages);

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    void deleteMessagesForRoom(String roomId);
}

package com.danis.nadi.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.danis.nadi.data.db.entity.SharedFileEntity;
import java.util.List;

@Dao
public interface SharedFileDao {
    @Query("SELECT * FROM shared_files WHERE roomId = :roomId ORDER BY createdAt DESC")
    LiveData<List<SharedFileEntity>> getFilesForRoom(String roomId);

    @Query("SELECT * FROM shared_files WHERE roomId = :roomId ORDER BY createdAt DESC")
    List<SharedFileEntity> getFilesForRoomOnce(String roomId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFile(SharedFileEntity file);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFiles(List<SharedFileEntity> files);

    @Query("DELETE FROM shared_files WHERE roomId = :roomId")
    void deleteFilesForRoom(String roomId);
}

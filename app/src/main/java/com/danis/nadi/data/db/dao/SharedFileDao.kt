package com.danis.nadi.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danis.nadi.data.db.entity.SharedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedFileDao {
    @Query("SELECT * FROM shared_files WHERE roomId = :roomId ORDER BY createdAt DESC")
    fun getFilesForRoom(roomId: String): Flow<List<SharedFileEntity>>

    @Query("SELECT * FROM shared_files WHERE roomId = :roomId ORDER BY createdAt DESC")
    suspend fun getFilesForRoomOnce(roomId: String): List<SharedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SharedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<SharedFileEntity>)

    @Query("DELETE FROM shared_files WHERE roomId = :roomId")
    suspend fun deleteFilesForRoom(roomId: String)
}

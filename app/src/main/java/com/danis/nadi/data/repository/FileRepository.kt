package com.danis.nadi.data.repository

import androidx.lifecycle.asFlow
import com.danis.nadi.data.db.dao.SharedFileDao
import com.danis.nadi.data.db.entity.SharedFileEntity
import com.danis.nadi.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FileRepository(private val sharedFileDao: SharedFileDao) {

    fun getFilesForRoom(roomId: String): Flow<List<TransferItem>> {
        return sharedFileDao.getFilesForRoom(roomId).asFlow().map { entities ->
            entities?.map { it.toDomain() } ?: emptyList()
        }
    }

    suspend fun getFilesForRoomOnce(roomId: String): List<TransferItem> = withContext(Dispatchers.IO) {
        sharedFileDao.getFilesForRoomOnce(roomId)?.map { it.toDomain() } ?: emptyList()
    }

    suspend fun saveFile(roomId: String, file: TransferItem) = withContext(Dispatchers.IO) {
        sharedFileDao.insertFile(SharedFileEntity.fromDomain(file, roomId))
    }

    suspend fun saveFiles(roomId: String, files: List<TransferItem>) = withContext(Dispatchers.IO) {
        sharedFileDao.insertFiles(files.map { SharedFileEntity.fromDomain(it, roomId) })
    }

    suspend fun clearFilesForRoom(roomId: String) = withContext(Dispatchers.IO) {
        sharedFileDao.deleteFilesForRoom(roomId)
    }
}

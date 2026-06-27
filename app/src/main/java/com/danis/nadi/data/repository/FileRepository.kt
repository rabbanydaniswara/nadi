package com.danis.nadi.data.repository

import com.danis.nadi.data.db.dao.SharedFileDao
import com.danis.nadi.data.db.entity.SharedFileEntity
import com.danis.nadi.model.TransferItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileRepository(private val sharedFileDao: SharedFileDao) {

    fun getFilesForRoom(roomId: String): Flow<List<TransferItem>> {
        return sharedFileDao.getFilesForRoom(roomId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getFilesForRoomOnce(roomId: String): List<TransferItem> {
        return sharedFileDao.getFilesForRoomOnce(roomId).map { it.toDomain() }
    }

    suspend fun saveFile(roomId: String, file: TransferItem) {
        sharedFileDao.insertFile(SharedFileEntity.fromDomain(file, roomId))
    }

    suspend fun saveFiles(roomId: String, files: List<TransferItem>) {
        sharedFileDao.insertFiles(files.map { SharedFileEntity.fromDomain(it, roomId) })
    }

    suspend fun clearFilesForRoom(roomId: String) {
        sharedFileDao.deleteFilesForRoom(roomId)
    }
}

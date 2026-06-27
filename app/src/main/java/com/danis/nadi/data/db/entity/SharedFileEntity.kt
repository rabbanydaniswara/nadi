package com.danis.nadi.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferItem
import com.danis.nadi.model.TransferStatus

@Entity(tableName = "shared_files")
data class SharedFileEntity(
    @PrimaryKey val transferId: String,
    val roomId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val direction: String,
    val status: String,
    val progress: Int,
    val createdAt: Long,
    val localUri: String?,
    val senderName: String?
) {
    fun toDomain(): TransferItem {
        return TransferItem(
            transferId = transferId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            direction = runCatching { TransferDirection.valueOf(direction) }.getOrDefault(TransferDirection.SHARED),
            status = runCatching { TransferStatus.valueOf(status) }.getOrDefault(TransferStatus.PENDING),
            progress = progress,
            createdAt = createdAt,
            localUri = localUri,
            senderName = senderName
        )
    }

    companion object {
        fun fromDomain(domain: TransferItem, roomId: String): SharedFileEntity {
            return SharedFileEntity(
                transferId = domain.transferId,
                roomId = roomId,
                fileName = domain.fileName,
                mimeType = domain.mimeType,
                sizeBytes = domain.sizeBytes,
                direction = domain.direction.name,
                status = domain.status.name,
                progress = domain.progress,
                createdAt = domain.createdAt,
                localUri = domain.localUri,
                senderName = domain.senderName
            )
        }
    }
}

package com.danis.nadi.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.danis.nadi.model.TransferDirection;
import com.danis.nadi.model.TransferItem;
import com.danis.nadi.model.TransferStatus;

import androidx.room.Ignore;

@Entity(tableName = "shared_files")
public class SharedFileEntity {
    @PrimaryKey
    @NonNull
    public String transferId;
    
    @NonNull
    public String roomId;
    
    @NonNull
    public String fileName;
    
    @Nullable
    public String mimeType;
    
    public long sizeBytes;
    
    @NonNull
    public String direction;
    
    @NonNull
    public String status;
    
    public int progress;
    
    public long createdAt;
    
    @Nullable
    public String localUri;
    
    @Nullable
    public String senderName;

    public SharedFileEntity() {}

    @Ignore
    public SharedFileEntity(@NonNull String transferId, @NonNull String roomId, @NonNull String fileName,
                            @Nullable String mimeType, long sizeBytes, @NonNull String direction,
                            @NonNull String status, int progress, long createdAt, @Nullable String localUri,
                            @Nullable String senderName) {
        this.transferId = transferId;
        this.roomId = roomId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.direction = direction;
        this.status = status;
        this.progress = progress;
        this.createdAt = createdAt;
        this.localUri = localUri;
        this.senderName = senderName;
    }

    public TransferItem toDomain() {
        TransferDirection dir;
        try {
            dir = TransferDirection.valueOf(direction);
        } catch (IllegalArgumentException e) {
            dir = TransferDirection.SHARED;
        }

        TransferStatus stat;
        try {
            stat = TransferStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            stat = TransferStatus.PENDING;
        }

        return new TransferItem(
            transferId,
            fileName,
            mimeType,
            sizeBytes,
            dir,
            stat,
            progress,
            createdAt,
            localUri,
            senderName
        );
    }

    public static SharedFileEntity fromDomain(TransferItem domain, String roomId) {
        return new SharedFileEntity(
            domain.getTransferId(),
            roomId,
            domain.getFileName(),
            domain.getMimeType(),
            domain.getSizeBytes(),
            domain.getDirection().name(),
            domain.getStatus().name(),
            domain.getProgress(),
            domain.getCreatedAt(),
            domain.getLocalUri(),
            domain.getSenderName()
        );
    }
}

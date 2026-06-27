package com.danis.nadi.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.danis.nadi.model.ChatMessage;

import androidx.room.Ignore;

@Entity(tableName = "chat_messages")
public class ChatMessageEntity {
    @PrimaryKey
    @NonNull
    public String messageId;
    
    @NonNull
    public String roomId;
    
    @NonNull
    public String senderId;
    
    @NonNull
    public String senderName;
    
    @NonNull
    public String text;
    
    public long createdAt;
    
    @NonNull
    public String status;
    
    @Nullable
    public String attachmentTransferId;
    
    @Nullable
    public String attachmentFileName;
    
    @Nullable
    public String attachmentMimeType;
    
    public long attachmentSizeBytes = -1L;
    
    @NonNull
    public String attachmentStatus = "";

    public ChatMessageEntity() {}

    @Ignore
    public ChatMessageEntity(@NonNull String messageId, @NonNull String roomId, @NonNull String senderId,
                             @NonNull String senderName, @NonNull String text, long createdAt, @NonNull String status,
                             @Nullable String attachmentTransferId, @Nullable String attachmentFileName,
                             @Nullable String attachmentMimeType, long attachmentSizeBytes, @NonNull String attachmentStatus) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.createdAt = createdAt;
        this.status = status;
        this.attachmentTransferId = attachmentTransferId;
        this.attachmentFileName = attachmentFileName;
        this.attachmentMimeType = attachmentMimeType;
        this.attachmentSizeBytes = attachmentSizeBytes;
        this.attachmentStatus = attachmentStatus;
    }

    public ChatMessage toDomain() {
        return new ChatMessage(
            messageId,
            senderId,
            senderName,
            text,
            createdAt,
            status,
            attachmentTransferId,
            attachmentFileName,
            attachmentMimeType,
            attachmentSizeBytes,
            attachmentStatus
        );
    }

    public static ChatMessageEntity fromDomain(ChatMessage domain, String roomId) {
        return new ChatMessageEntity(
            domain.getMessageId(),
            roomId,
            domain.getSenderId(),
            domain.getSenderName(),
            domain.getText(),
            domain.getCreatedAt(),
            domain.getStatus(),
            domain.getAttachmentTransferId(),
            domain.getAttachmentFileName(),
            domain.getAttachmentMimeType(),
            domain.getAttachmentSizeBytes(),
            domain.getAttachmentStatus()
        );
    }
}

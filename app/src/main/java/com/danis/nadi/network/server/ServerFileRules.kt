package com.danis.nadi.network.server

internal object ServerFileRules {
    const val MAX_FILE_ROOM_UPLOAD_BYTES = 100L * 1024L * 1024L
    const val MAX_CHAT_ATTACHMENT_BYTES = 10L * 1024L * 1024L
    const val CHAT_ATTACHMENT_TTL_MILLIS = 24L * 60L * 60L * 1000L
    const val MAX_CHAT_ATTACHMENT_STORAGE_BYTES = 250L * 1024L * 1024L
    const val FILE_ROOM_RECEIVED_FOLDER = "received"
    const val CHAT_DOWNLOADS_FOLDER = "chat-attachments"

    private val previewableImageMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    )

    private val previewableImageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")

    private val allowedChatAttachmentExtensions = setOf(
        "jpg",
        "jpeg",
        "png",
        "gif",
        "webp",
        "pdf",
        "txt",
        "doc",
        "docx",
        "ppt",
        "pptx",
        "xls",
        "xlsx",
        "zip"
    )

    fun isAllowedChatAttachment(fileName: String, sizeBytes: Long): Boolean {
        return isSafeOriginalFileName(fileName) &&
            isAllowedChatAttachmentName(fileName) &&
            sizeBytes in 0..MAX_CHAT_ATTACHMENT_BYTES
    }

    fun isAllowedChatAttachmentName(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in allowedChatAttachmentExtensions
    }

    fun isSafeOriginalFileName(fileName: String): Boolean {
        val trimmed = fileName.trim()
        if (trimmed.isBlank() || trimmed.length > 180) return false
        if (trimmed == "." || trimmed == "..") return false
        if (trimmed.any { it.code < 32 }) return false
        if (trimmed.any { it == '/' || it == '\\' || it == ':' }) return false
        return true
    }

    fun isPreviewableImageMime(mimeType: String?): Boolean {
        return mimeType.orEmpty().lowercase() in previewableImageMimeTypes
    }

    fun isPreviewableImageName(fileName: String): Boolean {
        return fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase() in previewableImageExtensions
    }

    fun resolvedUploadMimeType(fileName: String, declaredMimeType: String?): String {
        return declaredMimeType
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "application/octet-stream" && !it.startsWith("multipart/") }
            ?: inferredMimeType(fileName)
    }

    fun inferredMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}

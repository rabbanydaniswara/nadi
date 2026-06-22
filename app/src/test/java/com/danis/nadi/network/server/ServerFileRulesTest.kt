package com.danis.nadi.network.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFileRulesTest {
    @Test
    fun chatAttachmentAllowsKnownExtensionsWithinSizeLimit() {
        assertTrue(ServerFileRules.isAllowedChatAttachment("materi.PDF", 1024L))
        assertTrue(ServerFileRules.isAllowedChatAttachment("foto.webp", ServerFileRules.MAX_CHAT_ATTACHMENT_BYTES))
    }

    @Test
    fun chatAttachmentRejectsUnsupportedExtensionOrOversizedFile() {
        assertFalse(ServerFileRules.isAllowedChatAttachment("script.apk", 1024L))
        assertFalse(ServerFileRules.isAllowedChatAttachment("../materi.pdf", 1024L))
        assertFalse(
            ServerFileRules.isAllowedChatAttachment(
                "materi.pdf",
                ServerFileRules.MAX_CHAT_ATTACHMENT_BYTES + 1
            )
        )
    }

    @Test
    fun resolvedUploadMimeTypePrefersUsefulDeclaredMimeType() {
        assertEquals(
            "application/pdf",
            ServerFileRules.resolvedUploadMimeType("materi.bin", "application/pdf; charset=utf-8")
        )
    }

    @Test
    fun resolvedUploadMimeTypeFallsBackForMultipartOrOctetStream() {
        assertEquals(
            "image/png",
            ServerFileRules.resolvedUploadMimeType("foto.png", "multipart/form-data")
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ServerFileRules.resolvedUploadMimeType("proposal.docx", "application/octet-stream")
        )
    }

    @Test
    fun previewableImagesCanBeDetectedByMimeTypeOrName() {
        assertTrue(ServerFileRules.isPreviewableImageMime("image/jpeg"))
        assertTrue(ServerFileRules.isPreviewableImageName("lampiran.JPG"))
        assertFalse(ServerFileRules.isPreviewableImageMime("application/pdf"))
        assertFalse(ServerFileRules.isPreviewableImageName("materi.pdf"))
    }
}

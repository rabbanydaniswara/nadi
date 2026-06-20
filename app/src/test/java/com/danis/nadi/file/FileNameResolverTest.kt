package com.danis.nadi.file

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameResolverTest {
    @Test
    fun uniqueNameReturnsOriginalWhenAvailable() {
        val result = FileNameResolver.uniqueName("materi.pdf") { false }

        assertEquals("materi.pdf", result)
    }

    @Test
    fun uniqueNameAddsSuffixBeforeExtension() {
        val existing = setOf("materi.pdf", "materi (1).pdf")

        val result = FileNameResolver.uniqueName("materi.pdf") { it in existing }

        assertEquals("materi (2).pdf", result)
    }
}

package com.danis.nadi.file

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormatterTest {
    @Test
    fun formatShowsBytes() {
        assertEquals("512 B", FileSizeFormatter.format(512))
    }

    @Test
    fun formatShowsMegabytes() {
        assertEquals("1.5 MB", FileSizeFormatter.format(1572864))
    }
}

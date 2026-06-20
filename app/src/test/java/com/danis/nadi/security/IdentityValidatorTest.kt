package com.danis.nadi.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityValidatorTest {
    @Test
    fun validIdentityNormalizesNimAndName() {
        val identity = IdentityValidator.validate("  22 01-ABC  ", "  Rabbany   Daniswara ")

        assertEquals("2201-ABC", identity?.nim)
        assertEquals("Rabbany Daniswara", identity?.name)
        assertEquals("2201-ABC - Rabbany Daniswara", identity?.displayName)
    }

    @Test
    fun invalidIdentityIsRejected() {
        assertNull(IdentityValidator.validate("", "Danis"))
        assertNull(IdentityValidator.validate("22", "Danis"))
        assertNull(IdentityValidator.validate("2201", "D"))
        assertNull(IdentityValidator.validate("2201<script>", "Danis"))
        assertNull(IdentityValidator.validate("2201", "123"))
    }

    @Test
    fun commonStudentNamesAreAccepted() {
        assertTrue(IdentityValidator.validate("22010001", "Siti Aisyah") != null)
        assertTrue(IdentityValidator.validate("22010002", "Dwi Putra") != null)
        assertTrue(IdentityValidator.validate("22010003", "Nurul Azizah") != null)
    }
}

package com.danis.nadi.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinValidatorTest {
    private val validator = PinValidator()

    @Test
    fun blankConfiguredPinAllowsJoin() {
        assertTrue(validator.isConfiguredPinValid(null, null))
        assertTrue(validator.isConfiguredPinValid("", "123456"))
    }

    @Test
    fun configuredPinRequiresMatchingSubmittedPin() {
        assertTrue(validator.isConfiguredPinValid("123456", "123456"))
        assertFalse(validator.isConfiguredPinValid("123456", "654321"))
        assertFalse(validator.isConfiguredPinValid("123456", null))
    }
}

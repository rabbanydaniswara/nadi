package com.danis.nadi.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenGeneratorTest {
    private val generator = TokenGenerator()

    @Test
    fun newTokenUsesRequestedLength() {
        val token = generator.newToken(length = 40)

        assertEquals(40, token.length)
    }

    @Test
    fun newTokenProducesDifferentValues() {
        val first = generator.newToken()
        val second = generator.newToken()

        assertNotEquals(first, second)
    }

    @Test
    fun newPinUsesDigitsOnly() {
        val pin = generator.newPin(length = 6)

        assertEquals(6, pin.length)
        assertTrue(pin.all { it.isDigit() })
    }
}

package com.danis.nadi.security

import java.security.SecureRandom

class TokenGenerator(
    private val random: SecureRandom = SecureRandom()
) {
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    fun newToken(length: Int = 32): String = randomString(length)

    fun newSessionId(length: Int = 12): String = randomString(length)

    fun newPin(length: Int = 6): String {
        require(length in 4..8) { "PIN length must be between 4 and 8 digits." }
        return buildString(length) {
            repeat(length) {
                append(random.nextInt(10))
            }
        }
    }

    private fun randomString(length: Int): String {
        require(length > 0) { "Token length must be positive." }
        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }
}

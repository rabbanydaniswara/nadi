package com.danis.nadi.security

class PinValidator {
    fun isConfiguredPinValid(configuredPin: String?, submittedPin: String?): Boolean {
        if (configuredPin.isNullOrBlank()) return true
        return configuredPin == submittedPin?.trim()
    }
}

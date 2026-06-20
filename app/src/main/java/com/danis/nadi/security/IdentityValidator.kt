package com.danis.nadi.security

import com.danis.nadi.model.ClientIdentity

object IdentityValidator {
    private val nimPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,31}$")
    private val namePattern = Regex("^[\\p{L}][\\p{L} .'-]{1,79}$")

    fun validate(nim: String?, name: String?): ClientIdentity? {
        val cleanNim = nim.orEmpty().trim().replace(Regex("\\s+"), "")
        val cleanName = name.orEmpty().trim().replace(Regex("\\s+"), " ")
        if (!nimPattern.matches(cleanNim)) return null
        if (!namePattern.matches(cleanName)) return null
        return ClientIdentity(cleanNim, cleanName)
    }
}

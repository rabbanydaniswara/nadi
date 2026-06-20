package com.danis.nadi.model

data class ClientIdentity(
    val nim: String,
    val name: String
) {
    val displayName: String
        get() = "$nim - $name"
}

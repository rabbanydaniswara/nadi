package com.danis.nadi.room

enum class RoomLifecycleState {
    IDLE,
    PREPARING,
    STARTING_NETWORK,
    STARTING_SERVER,
    ACTIVE,
    STOPPING,
    STOPPED,
    FAILED
}

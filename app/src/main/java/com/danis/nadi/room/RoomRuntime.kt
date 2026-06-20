package com.danis.nadi.room

import android.content.Context

object RoomRuntime {
    @Volatile
    private var controller: RoomController? = null

    fun controller(context: Context): RoomController {
        return controller ?: synchronized(this) {
            controller ?: RoomController(context.applicationContext).also {
                controller = it
            }
        }
    }
}

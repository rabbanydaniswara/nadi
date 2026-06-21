package com.danis.nadi.settings

import android.content.Context
import com.danis.nadi.room.NetworkMode

class NadiSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun settings(): NadiSettings {
        val mode = runCatching {
            NetworkMode.valueOf(preferences.getString(KEY_DEFAULT_NETWORK_MODE, NetworkMode.SAME_WIFI.name).orEmpty())
        }.getOrDefault(NetworkMode.SAME_WIFI)
        return NadiSettings(
            defaultHostName = preferences.getString(KEY_DEFAULT_HOST_NAME, DEFAULT_HOST_NAME).orEmpty()
                .ifBlank { DEFAULT_HOST_NAME },
            defaultNetworkMode = mode,
            fileRoomTreeUri = preferences.getString(KEY_FILE_ROOM_TREE_URI, null)
        )
    }

    fun save(settings: NadiSettings) {
        preferences.edit()
            .putString(KEY_DEFAULT_HOST_NAME, settings.defaultHostName.trim().ifBlank { DEFAULT_HOST_NAME })
            .putString(KEY_DEFAULT_NETWORK_MODE, settings.defaultNetworkMode.name)
            .putString(KEY_FILE_ROOM_TREE_URI, settings.fileRoomTreeUri)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "nadi_settings"
        const val KEY_DEFAULT_HOST_NAME = "default_host_name"
        const val KEY_DEFAULT_NETWORK_MODE = "default_network_mode"
        const val KEY_FILE_ROOM_TREE_URI = "file_room_tree_uri"
        const val DEFAULT_HOST_NAME = "Host Nadi"
    }
}

data class NadiSettings(
    val defaultHostName: String,
    val defaultNetworkMode: NetworkMode,
    val fileRoomTreeUri: String? = null
)

package com.danis.nadi

import android.widget.Toast
import com.danis.nadi.room.NetworkMode
import com.danis.nadi.settings.NadiSettings

fun MainActivity.applySettingsToSetup() {
    val settings = settingsStore.settings()
    hostNameInput.setText(settings.defaultHostName)
    val checkedId = when (settings.defaultNetworkMode) {
        NetworkMode.HOTSPOT -> R.id.hotspotModeRadio
        NetworkMode.SAME_WIFI,
        NetworkMode.SAME_WIFI_FALLBACK -> R.id.sameWifiModeRadio
    }
    networkModeGroup.check(checkedId)
    networkModeHelpText.text = if (checkedId == R.id.hotspotModeRadio) {
        getString(R.string.hotspot_permission_reason)
    } else {
        getString(R.string.same_wifi_note)
    }
}

fun MainActivity.applySettingsToSettingsScreen() {
    val settings = settingsStore.settings()
    defaultHostNameInput.setText(settings.defaultHostName)
    defaultNetworkModeGroup.check(
        if (settings.defaultNetworkMode == NetworkMode.HOTSPOT) {
            R.id.defaultHotspotModeRadio
        } else {
            R.id.defaultSameWifiModeRadio
        }
    )
    fileRoomStorageText.text = settings.fileRoomTreeUri?.let {
        getString(R.string.file_room_storage_custom, it)
    } ?: getString(R.string.file_room_storage_default)
}

fun MainActivity.saveSettings() {
    val mode = if (defaultNetworkModeGroup.checkedRadioButtonId == R.id.defaultHotspotModeRadio) {
        NetworkMode.HOTSPOT
    } else {
        NetworkMode.SAME_WIFI
    }
    settingsStore.save(
        NadiSettings(
            defaultHostName = defaultHostNameInput.text?.toString().orEmpty(),
            defaultNetworkMode = mode,
            fileRoomTreeUri = settingsStore.settings().fileRoomTreeUri
        )
    )
    applySettingsToSetup()
    Toast.makeText(this, "Pengaturan disimpan.", Toast.LENGTH_SHORT).show()
    showHome()
}

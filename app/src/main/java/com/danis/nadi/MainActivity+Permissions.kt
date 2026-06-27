package com.danis.nadi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun MainActivity.hasHotspotPermissions(): Boolean {
    return requiredHotspotPermissions().all { hasPermission(it) }
}

fun MainActivity.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun MainActivity.requiredHotspotPermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()
}

fun MainActivity.requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val permission = Manifest.permission.POST_NOTIFICATIONS
    if (!hasPermission(permission)) {
        notificationPermissionLauncher.launch(permission)
    }
}

internal fun String.isValidRoomPin(): Boolean = matches(Regex("^\\d{4,8}$"))


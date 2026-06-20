package com.danis.nadi.history

import android.content.Context
import com.danis.nadi.model.TransferDirection
import com.danis.nadi.model.TransferStatus
import org.json.JSONArray
import org.json.JSONObject

class NadiHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveRecentTransfers(items: List<TransferHistoryItem>) {
        val deduped = items
            .distinctBy { it.transferId }
            .sortedByDescending { it.createdAt }
            .take(MAX_RECENT_TRANSFERS)
        val payload = JSONArray()
        deduped.forEach { payload.put(it.toJson()) }
        preferences.edit().putString(KEY_RECENT_TRANSFERS, payload.toString()).apply()
    }

    fun recentTransfers(): List<TransferHistoryItem> {
        val payload = preferences.getString(KEY_RECENT_TRANSFERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(payload)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toHistoryItem()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        preferences.edit().remove(KEY_RECENT_TRANSFERS).apply()
    }

    private fun TransferHistoryItem.toJson(): JSONObject {
        return JSONObject()
            .put("transferId", transferId)
            .put("fileName", fileName)
            .put("sizeBytes", sizeBytes)
            .put("direction", direction.name)
            .put("status", status.name)
            .put("progress", progress)
            .put("createdAt", createdAt)
            .put("senderName", senderName)
    }

    private fun JSONObject.toHistoryItem(): TransferHistoryItem? {
        val direction = runCatching { TransferDirection.valueOf(getString("direction")) }.getOrNull() ?: return null
        val status = runCatching { TransferStatus.valueOf(getString("status")) }.getOrNull() ?: return null
        return TransferHistoryItem(
            transferId = optString("transferId"),
            fileName = optString("fileName"),
            sizeBytes = optLong("sizeBytes", -1L),
            direction = direction,
            status = status,
            progress = optInt("progress", 0),
            createdAt = optLong("createdAt", 0L),
            senderName = optString("senderName").takeIf { it.isNotBlank() }
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "nadi_history"
        const val KEY_RECENT_TRANSFERS = "recent_transfers"
        const val MAX_RECENT_TRANSFERS = 20
    }
}

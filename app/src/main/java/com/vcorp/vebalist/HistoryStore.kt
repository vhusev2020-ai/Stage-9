package com.vcorp.vebalist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PublishHistoryItem(
    val sku: String,
    val title: String,
    val listingId: String?,
    val timestamp: Long,
    val success: Boolean,
    val error: String?
)

object HistoryStore {
    private const val PREFS = "vebalist_history"
    private const val KEY = "items"

    fun add(context: Context, item: PublishHistoryItem) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val obj = JSONObject()
            .put("sku", item.sku)
            .put("title", item.title)
            .put("listingId", item.listingId)
            .put("timestamp", item.timestamp)
            .put("success", item.success)
            .put("error", item.error)
        arr.put(obj)
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun list(context: Context): List<PublishHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val out = mutableListOf<PublishHistoryItem>()
        for (i in arr.length()-1 downTo 0) {
            val x = arr.getJSONObject(i)
            out += PublishHistoryItem(
                sku = x.optString("sku"),
                title = x.optString("title"),
                listingId = x.optString("listingId").takeIf { it.isNotBlank() },
                timestamp = x.optLong("timestamp"),
                success = x.optBoolean("success"),
                error = x.optString("error").takeIf { it.isNotBlank() }
            )
        }
        return out
    }
}

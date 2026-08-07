package com.vcorp.vebalist

import android.content.Context

object AppPrefs {
    private const val PREFS = "vebalist_prefs"
    private const val BACKEND_URL = "backend_url"

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(BACKEND_URL, "") ?: ""

    fun setBackendUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(BACKEND_URL, value.trim().trimEnd('/')).apply()
    }
}

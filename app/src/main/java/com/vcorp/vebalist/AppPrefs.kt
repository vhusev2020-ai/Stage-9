package com.vcorp.vebalist

import android.content.Context

object AppPrefs {
    private const val PREFS = "vebalist_prefs"
    private const val BACKEND_URL = "backend_url"
    private const val API_KEY = "api_key"

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(BACKEND_URL, BuildConfig.BACKEND_URL)
            ?.ifBlank { BuildConfig.BACKEND_URL }
            ?: BuildConfig.BACKEND_URL

    fun setBackendUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(BACKEND_URL, value.trim().trimEnd('/')).apply()
    }

    fun getApiKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(API_KEY, "") ?: ""

    fun setApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(API_KEY, value.trim()).apply()
    }
}

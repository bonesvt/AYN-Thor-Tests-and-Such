package com.bonesvt.thordual

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("thordual", Context.MODE_PRIVATE)

    var profileId: String
        get() = prefs.getString("profile", DEFAULT_PROFILE) ?: DEFAULT_PROFILE
        set(value) = prefs.edit().putString("profile", value).apply()

    var layoutIndex: Int
        get() = prefs.getInt("layout", 0)
        set(value) = prefs.edit().putInt("layout", value).apply()

    /** Display to draw on; [AUTO_DISPLAY] picks the first secondary display. */
    var displayId: Int
        get() = prefs.getInt("display", AUTO_DISPLAY)
        set(value) = prefs.edit().putInt("display", value).apply()

    /** Capture resolution relative to the top screen. Lower saves GPU at the cost of sharpness. */
    var captureScale: Float
        get() = prefs.getFloat("captureScale", 1f)
        set(value) = prefs.edit().putFloat("captureScale", value).apply()

    companion object {
        const val DEFAULT_PROFILE = "jak-x-combat-racing"
        const val AUTO_DISPLAY = -1
    }
}

package com.pixelatedcheese1.islandroid

import android.content.Context
import android.content.SharedPreferences

class Feature(val key: String, val title: String, val desc: String, val default: Boolean = true)

object IslandPrefs {
    private var sp: SharedPreferences? = null

    val FEATURES = listOf(
        Feature("media", "Now playing", "Album art, waveform, scrubber and transport controls"),
        Feature("call", "Calls", "Incoming call with answer / decline, ongoing call timer"),
        Feature("live", "Live activities", "Timers, stopwatch, navigation, rides, downloads, any ongoing progress notification"),
        Feature("charging", "Charging", "Expands when a charger is connected"),
        Feature("battery", "Low battery", "Alert at 20% when unplugged"),
        Feature("audio", "Earbuds / headphones", "Connection card with battery level when the phone reports it"),
        Feature("ringer", "Silent / ring", "Bell animation when the ringer mode changes"),
        Feature("focus", "Do Not Disturb", "Shows when the interruption filter changes"),
        Feature("airplane", "Airplane mode", "Shows when toggled"),
        Feature("hotspot", "Hotspot", "Shown while the hotspot is on (best-effort)"),
        Feature("unlock", "Unlock", "Lock → unlock animation (Face ID style)"),
        Feature("privacy", "Camera / mic indicator", "Dot while the camera or microphone is in use"),
        Feature("screenrec", "Screen recording", "Red timer while recording (best-effort)"),
        Feature("notif", "Notification peek", "Brief banner for new notifications (not on iOS, handy on Android)")
    )

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
    }

    val prefs: SharedPreferences get() = sp!!

    var theme: Int
        get() = prefs.getInt("theme", 0)            // 0 = iOS, 1 = Material 3 Expressive
        set(v) = prefs.edit().putInt("theme", v).apply()

    var master: Boolean
        get() = prefs.getBoolean("master", true)
        set(v) = prefs.edit().putBoolean("master", v).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    var offX: Int                                    // dp
        get() = prefs.getInt("offX", 0)
        set(v) = prefs.edit().putInt("offX", v).apply()

    var offY: Int                                    // dp
        get() = prefs.getInt("offY", 0)
        set(v) = prefs.edit().putInt("offY", v).apply()

    var extraW: Int                                  // dp added around the camera hole
        get() = prefs.getInt("extraW", 0)
        set(v) = prefs.edit().putInt("extraW", v).apply()

    fun feature(key: String): Boolean =
        prefs.getBoolean("f_$key", FEATURES.firstOrNull { it.key == key }?.default ?: true)

    fun setFeature(key: String, v: Boolean) = prefs.edit().putBoolean("f_$key", v).apply()
}

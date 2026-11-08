package com.pixelatedcheese1.islandroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock

/** Fake events so you can try every presentation without waiting for real ones. */
object Demo {
    private var mediaStart = SystemClock.elapsedRealtime()
    private var mediaPausedAt = 0L

    private fun art(): Bitmap {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, 0xFFFF5E7E.toInt(), 0xFF5B3FD6.toInt(), Shader.TileMode.CLAMP) }
        Canvas(b).drawRect(0f, 0f, 256f, 256f, p)
        return b
    }
    private val cover by lazy { art() }

    fun media(playing: Boolean = true) {
        val dur = 244_000L
        if (playing && mediaPausedAt != 0L) { mediaStart = SystemClock.elapsedRealtime() - mediaPausedAt; mediaPausedAt = 0 }
        if (!playing) mediaPausedAt = SystemClock.elapsedRealtime() - mediaStart
        val e = IslandEvent("media", Kind.MEDIA).apply {
            title = "Midnight City"; subtitle = "M83"; art = cover; accent = 0xFFFF7A93.toInt()
            this.playing = playing; duration = dur; priority = 40
            position = { if (this.playing) (SystemClock.elapsedRealtime() - mediaStart) % dur else mediaPausedAt }
            actions = listOf(
                IslandAction("Previous") { mediaStart = SystemClock.elapsedRealtime() },
                IslandAction("Play/Pause") { media(!playing) },
                IslandAction("Next") { mediaStart = SystemClock.elapsedRealtime() }
            )
        }
        IslandBus.post(e)
    }

    fun call() = IslandBus.post(IslandEvent("call", Kind.CALL_INCOMING).apply {
        title = "Alex Rivera"; subtitle = "mobile"; priority = 100; autoExpand = true
        actions = listOf(
            IslandAction("Decline", Icon.PHONE, IslandAction.Style.DANGER) { IslandBus.remove("call") },
            IslandAction("Accept", Icon.PHONE, IslandAction.Style.POSITIVE) { ongoingCall() }
        )
    })

    private fun ongoingCall() = IslandBus.post(IslandEvent("call", Kind.CALL_ONGOING).apply {
        title = "Alex Rivera"; priority = 70; chronoBase = SystemClock.elapsedRealtime()
        actions = listOf(IslandAction("End", Icon.PHONE, IslandAction.Style.DANGER) { IslandBus.remove("call") })
    })

    fun timer() = IslandBus.post(IslandEvent("live:timer", Kind.LIVE).apply {
        title = "Timer"; subtitle = "Pasta"; priority = 50; accent = 0xFFFF9F0A.toInt()
        chronoBase = SystemClock.elapsedRealtime() + 5 * 60_000L; countDown = true
        actions = listOf(IslandAction("Stop", null, IslandAction.Style.DANGER) { IslandBus.remove("live:timer") })
    })

    fun navigation() = IslandBus.post(IslandEvent("live:nav", Kind.LIVE).apply {
        title = "Turn left onto Douglas Ave"; subtitle = "400 ft"; priority = 55; accent = 0xFF30D158.toInt()
        progress = 0.6f
    })

    fun charging() = IslandBus.post(IslandEvent("charging", Kind.CHARGING).apply {
        title = "Charging"; subtitle = "Battery"; level = 64; accent = 0xFF30D158.toInt()
        priority = 90; timeoutMs = 3500; autoExpand = true
    })

    fun lowBattery() = IslandBus.post(IslandEvent("lowbat", Kind.LOW_BATTERY).apply {
        title = "Low Battery"; subtitle = "Connect a charger"; level = 19; priority = 90; timeoutMs = 4000; autoExpand = true
    })

    fun earbuds() = IslandBus.post(IslandEvent("bt", Kind.AUDIO_DEVICE).apply {
        title = "Pixel Buds Pro"; subtitle = "Connected"; level = 82; priority = 90; timeoutMs = 4000; autoExpand = true
    })

    fun silent(muted: Boolean) = IslandBus.post(IslandEvent("ringer", Kind.RINGER).apply {
        title = if (muted) "Silent" else "Ring"; this.muted = muted; priority = 90; timeoutMs = 2200; autoExpand = true
    })

    fun dnd() = IslandBus.post(IslandEvent("focus", Kind.FOCUS).apply {
        title = "Do Not Disturb"; subtitle = "On"; priority = 90; timeoutMs = 2500; autoExpand = true
    })

    fun airplane() = IslandBus.post(IslandEvent("airplane", Kind.AIRPLANE).apply {
        title = "Airplane Mode"; subtitle = "On"; priority = 90; timeoutMs = 2500; autoExpand = true
    })

    fun hotspot() = IslandBus.post(IslandEvent("hotspot", Kind.HOTSPOT).apply {
        title = "Personal Hotspot"; subtitle = "1 device connected"; priority = 30; timeoutMs = 8000
    })

    fun unlock() = IslandBus.post(IslandEvent("unlock", Kind.UNLOCK).apply { priority = 95; timeoutMs = 1300 })

    fun camera() = IslandBus.post(IslandEvent("privacy", Kind.PRIVACY).apply {
        title = "Camera in use"; accent = 0xFF30D158.toInt(); priority = 10; timeoutMs = 6000
    })

    fun recording() = IslandBus.post(IslandEvent("live:rec", Kind.SCREEN_REC).apply {
        title = "Screen Recording"; priority = 45; chronoBase = SystemClock.elapsedRealtime(); timeoutMs = 20000
        actions = listOf(IslandAction("Stop", Icon.STOP, IslandAction.Style.DANGER) { IslandBus.remove("live:rec") })
    })

    fun notification() = IslandBus.post(IslandEvent("notif", Kind.NOTIFICATION).apply {
        title = "Sam"; subtitle = "Are we still on for 6? I can grab a table near the window."
        priority = 60; timeoutMs = 5000; autoExpand = true
    })

    /** Music + timer together -> compact island with a detached bubble. */
    fun split() { media(true); timer() }

    fun clearAll() {
        listOf("media", "call", "live:timer", "live:nav", "live:rec", "charging", "lowbat", "bt", "ringer", "focus",
            "airplane", "hotspot", "unlock", "privacy", "notif").forEach { IslandBus.remove(it) }
    }
}

package com.pixelatedcheese1.islandroid

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

/** Everything the island can show. `pref` is the feature toggle key. */
enum class Kind(val pref: String) {
    CALL_INCOMING("call"), CALL_ONGOING("call"),
    MEDIA("media"), LIVE("live"), NOTIFICATION("notif"),
    CHARGING("charging"), LOW_BATTERY("battery"), AUDIO_DEVICE("audio"),
    RINGER("ringer"), FOCUS("focus"), AIRPLANE("airplane"), HOTSPOT("hotspot"),
    UNLOCK("unlock"), PRIVACY("privacy"), SCREEN_REC("screenrec")
}

class IslandAction(
    val label: String,
    val icon: Icon? = null,
    val style: Style = Style.NEUTRAL,
    val run: () -> Unit
) {
    enum class Style { NEUTRAL, DANGER, POSITIVE, PRIMARY }
}

class IslandEvent(val id: String, val kind: Kind) {
    var title = ""
    var subtitle = ""
    var icon: Drawable? = null          // app icon
    var art: Bitmap? = null             // album art / contact photo
    var accent = Color.WHITE
    var progress = -1f                  // 0..1, negative = none
    var chronoBase = 0L                 // SystemClock.elapsedRealtime() based, 0 = none
    var countDown = false
    var level = -1                      // battery percent
    var playing = false
    var muted = false                   // ringer: true = silent/vibrate
    var duration = 0L
    var position: (() -> Long)? = null
    var actions: List<IslandAction> = emptyList()
    var onTap: (() -> Unit)? = null
    var pkg: String? = null
    var priority = 50
    var timeoutMs = 0L                  // 0 = stays until removed
    var autoExpand = false

    internal var hidden = false         // swiped away until next update
    internal var seq = 0L
}

object IslandBus {
    interface Sink {
        fun post(e: IslandEvent)
        fun remove(id: String)
    }

    @Volatile
    var sink: Sink? = null
    private val main = Handler(Looper.getMainLooper())

    fun post(e: IslandEvent) { main.post { sink?.post(e) } }
    fun remove(id: String) { main.post { sink?.remove(id) } }
}

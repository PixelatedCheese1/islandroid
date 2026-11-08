package com.pixelatedcheese1.islandroid

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max
import kotlin.math.min

/** Turns media sessions + notifications into island events (calls, timers, navigation, now-playing …). */
class IslandNotificationListener : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var media: MediaMonitor? = null
    private val seen = HashSet<String>()
    private var callKey: String? = null

    override fun onListenerConnected() {
        IslandPrefs.init(this)
        media = MediaMonitor(this, main).also { it.start() }
        try { activeNotifications?.forEach { handle(it, true) } } catch (_: Throwable) {}
    }

    override fun onListenerDisconnected() {
        media?.stop(); media = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, false)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        IslandBus.remove("live:${sbn.key}")
        if (sbn.key == callKey) { IslandBus.remove("call"); callKey = null }
    }

    private fun handle(sbn: StatusBarNotification, initial: Boolean) {
        val n = sbn.notification ?: return
        val pkg = sbn.packageName
        if (pkg == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val template = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (template.endsWith("MediaStyle")) return           // handled by MediaMonitor

        val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString() ?: ""
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX)
        val pNow = ex.getInt(Notification.EXTRA_PROGRESS)
        val indet = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val hasProgress = pMax > 0 && !indet
        val cat = n.category ?: ""
        val appIcon = try { packageManager.getApplicationIcon(pkg) } catch (_: Throwable) { null }
        val big = try { n.getLargeIcon()?.loadDrawable(this)?.toBitmap(160, 160) } catch (_: Throwable) { null }

        fun fill(e: IslandEvent): IslandEvent = e.apply {
            this.title = title; subtitle = text; icon = appIcon; this.pkg = pkg; art = big
            onTap = { n.contentIntent?.fire(this@IslandNotificationListener) ?: Unit }
            if (n.color != 0) accent = n.color
        }

        val actions = (n.actions ?: emptyArray()).filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null }

        when {
            // ---- calls
            cat == Notification.CATEGORY_CALL || template.endsWith("CallStyle") -> {
                val type = ex.getInt("android.callType", 0)
                val incoming = type == 1 || (type == 0 && !chrono)
                val e = fill(IslandEvent("call", if (incoming) Kind.CALL_INCOMING else Kind.CALL_ONGOING))
                e.priority = if (incoming) 100 else 70
                e.autoExpand = incoming && !initial
                e.actions = actions.take(3).mapIndexed { i, a ->
                    val s = styleOf(a.title.toString(), i, actions.size)
                    IslandAction(a.title.toString(), if (s != IslandAction.Style.NEUTRAL) Icon.PHONE else null, s) { a.actionIntent.fire(this) }
                }
                if (chrono) e.chronoBase = SystemClock.elapsedRealtime() + (n.`when` - System.currentTimeMillis())
                callKey = sbn.key
                IslandBus.post(e)
            }
            // ---- screen recording (best effort – SystemUI's recording notification)
            pkg == "com.android.systemui" && (sbn.notification.channelId ?: "").contains("record", true) -> {
                val e = fill(IslandEvent("live:${sbn.key}", Kind.SCREEN_REC))
                e.title = "Screen Recording"; e.subtitle = ""
                e.priority = 45
                if (chrono) e.chronoBase = SystemClock.elapsedRealtime() + (n.`when` - System.currentTimeMillis())
                e.actions = actions.take(1).map { a -> IslandAction(a.title.toString(), Icon.STOP, IslandAction.Style.DANGER) { a.actionIntent.fire(this) } }
                IslandBus.post(e)
            }
            // ---- live activities: ongoing + (timer | progress | navigation)
            ongoing && (chrono || hasProgress || cat == Notification.CATEGORY_NAVIGATION || cat == "stopwatch") -> {
                val e = fill(IslandEvent("live:${sbn.key}", Kind.LIVE))
                e.priority = if (cat == Notification.CATEGORY_NAVIGATION) 55 else 50
                if (chrono) {
                    e.chronoBase = SystemClock.elapsedRealtime() + (n.`when` - System.currentTimeMillis())
                    e.countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
                }
                if (hasProgress) e.progress = pNow.toFloat() / pMax
                e.actions = actions.take(3).map { a -> IslandAction(a.title.toString()) { a.actionIntent.fire(this) } }
                IslandBus.post(e)
            }
            // ---- ordinary notification peek
            !ongoing && !initial -> {
                val rk = Ranking()
                val imp = if (currentRanking.getRanking(sbn.key, rk)) rk.importance else NotificationManager.IMPORTANCE_DEFAULT
                if (imp < NotificationManager.IMPORTANCE_DEFAULT) return
                if (title.isEmpty() && text.isEmpty()) return
                val first = seen.add(sbn.key)
                if (!first && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
                val e = fill(IslandEvent("notif", Kind.NOTIFICATION))
                e.priority = 60; e.timeoutMs = 5000; e.autoExpand = true
                e.actions = actions.take(2).map { a -> IslandAction(a.title.toString()) { a.actionIntent.fire(this) } }
                IslandBus.post(e)
            }
        }
    }

    private fun styleOf(title: String, idx: Int, count: Int): IslandAction.Style {
        val s = title.lowercase()
        return when {
            listOf("decline", "reject", "hang", "end", "dismiss", "ignore").any { s.contains(it) } -> IslandAction.Style.DANGER
            listOf("answer", "accept", "pick").any { s.contains(it) } -> IslandAction.Style.POSITIVE
            count == 2 -> if (idx == 0) IslandAction.Style.DANGER else IslandAction.Style.POSITIVE
            else -> IslandAction.Style.NEUTRAL
        }
    }
}

/** Watches active MediaSessions (Spotify, YouTube Music, podcasts, video players …). */
class MediaMonitor(private val svc: NotificationListenerService, private val main: Handler) {
    private val msm = svc.getSystemService(MediaSessionManager::class.java)
    private val cn = ComponentName(svc, IslandNotificationListener::class.java)
    private var controllers: List<MediaController> = emptyList()

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { setControllers(it ?: emptyList()) }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(listener, cn, main)
            setControllers(msm.getActiveSessions(cn))
        } catch (_: SecurityException) {}
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(listener) } catch (_: Throwable) {}
        controllers.forEach { try { it.unregisterCallback(cb) } catch (_: Throwable) {} }
        controllers = emptyList()
        IslandBus.remove("media")
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { try { it.unregisterCallback(cb) } catch (_: Throwable) {} }
        controllers = list
        controllers.forEach { it.registerCallback(cb, main) }
        publish()
    }

    private fun publish() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
            }
        if (c == null) { IslandBus.remove("media"); return }

        val md = c.metadata
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        val art: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val e = IslandEvent("media", Kind.MEDIA)
        e.title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Now Playing"
        e.subtitle = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        e.art = art
        e.accent = accentOf(art)
        e.playing = playing
        e.priority = 40
        e.timeoutMs = if (playing) 0 else 45_000
        e.pkg = c.packageName
        e.icon = try { svc.packageManager.getApplicationIcon(c.packageName) } catch (_: Throwable) { null }
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (dur > 0 && st != null) {
            e.duration = dur
            val base = st.position; val upd = st.lastPositionUpdateTime; val speed = st.playbackSpeed
            e.position = {
                if (playing) min(dur, base + ((SystemClock.elapsedRealtime() - upd) * speed).toLong()) else base
            }
        }
        val tc = c.transportControls
        e.actions = listOf(
            IslandAction("Previous") { tc.skipToPrevious() },
            IslandAction(if (playing) "Pause" else "Play") { if (playing) tc.pause() else tc.play() },
            IslandAction("Next") { tc.skipToNext() }
        )
        e.onTap = { c.sessionActivity?.fire(svc) ?: Unit }
        IslandBus.post(e)
    }

    private fun accentOf(b: Bitmap?): Int {
        if (b == null) return Color.WHITE
        return try {
            val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3); Color.colorToHSV(px, hsv)
            if (hsv[1] < 0.12f) return Color.WHITE
            hsv[1] = min(0.9f, max(hsv[1], 0.45f)); hsv[2] = max(hsv[2], 0.85f)
            Color.HSVToColor(hsv)
        } catch (_: Throwable) { Color.WHITE }
    }
}

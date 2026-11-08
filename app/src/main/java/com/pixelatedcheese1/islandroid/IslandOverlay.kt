package com.pixelatedcheese1.islandroid

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.View.MeasureSpec
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min

/**
 * Owns the overlay window, the event queue and the state machine:
 *   hidden -> compact (primary [+ detached bubble for a 2nd activity]) <-> expanded
 */
class IslandOverlay(private val ctx: Context) : IslandBus.Sink, IslandView.Callbacks {

    private class Geo(
        val screenW: Int, val cx: Int, val holeW: Int, val holeH: Int, val holeTop: Int,
        val compactH: Int, val top: Int
    )

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val main = Handler(Looper.getMainLooper())

    private val events = LinkedHashMap<String, IslandEvent>()
    private val timeouts = HashMap<String, Runnable>()
    private var expandedId: String? = null
    private var shownId: String? = null
    private var bubbleId: String? = null
    private var expandedNow = false
    private var seq = 0L
    private var hideToken = 0

    private var theme = Theme.of(ctx)
    private var content = Content(ctx, theme)
    private var root: FrameLayout? = null
    private var island: IslandView? = null
    private var bubble: BubbleView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var attached = false
    private val winRect = Rect()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        main.post { if (key == "theme") rebuild() else refresh() }
    }

    init {
        IslandPrefs.prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    // ------------------------------------------------------------------ public

    override fun post(e: IslandEvent) {
        if (!IslandPrefs.master || !IslandPrefs.feature(e.kind.pref)) return
        val old = events[e.id]
        e.seq = ++seq
        e.hidden = false
        events[e.id] = e
        timeouts.remove(e.id)?.let { main.removeCallbacks(it) }
        if (e.timeoutMs > 0) {
            val r = Runnable { remove(e.id) }
            timeouts[e.id] = r
            main.postDelayed(r, e.timeoutMs)
        }
        if (e.autoExpand) {
            expandedId = e.id
            if (old == null) haptic()
        }
        refresh()
    }

    override fun remove(id: String) {
        if (events.remove(id) == null) return
        timeouts.remove(id)?.let { main.removeCallbacks(it) }
        if (expandedId == id) expandedId = null
        refresh()
    }

    fun reconfigure() {
        detach()
        refresh()
    }

    fun destroy() {
        IslandPrefs.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        timeouts.values.forEach { main.removeCallbacks(it) }
        timeouts.clear(); events.clear()
        detach()
    }

    // ------------------------------------------------------------------ geometry (camera auto-detect)

    private fun allowed(): Boolean {
        if (!IslandPrefs.master) return false
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return true
        return d.rotation == Surface.ROTATION_0
    }

    private fun geo(): Geo {
        val screenW = wm.currentWindowMetrics.bounds.width()
        var cx = screenW / 2
        var hw = ctx.dp(12); var hh = ctx.dp(12); var ht = ctx.dp(10)
        val r = dm.getDisplay(Display.DEFAULT_DISPLAY)?.cutout?.boundingRectTop
        if (r != null && !r.isEmpty) {                    // real camera cutout reported by the system
            cx = r.centerX(); hw = r.width(); hh = r.height(); ht = r.top
        }
        cx += ctx.dp(IslandPrefs.offX)
        hw = max(ctx.dp(8), hw + ctx.dp(IslandPrefs.extraW))
        val edge = ht <= ctx.dp(1)                        // notch touching the top edge vs. floating punch-hole
        val ch = max(ctx.dp(34), if (edge) ht + hh + ctx.dp(4) else hh + ctx.dp(14))
        val top = (if (edge) 0 else max(ctx.dp(2), ht + hh / 2 - ch / 2)) + ctx.dp(IslandPrefs.offY)
        return Geo(screenW, cx, hw, hh, ht, ch, max(0, top))
    }

    private fun tiny(g: Geo): FloatArray {
        val h = g.holeH.toFloat()
        val w = g.holeW.toFloat()
        return floatArrayOf(g.cx - w / 2f, g.top + (g.compactH - h) / 2f, w, h, min(w, h) / 2f, 0f, 0f)
    }

    // ------------------------------------------------------------------ state machine

    private fun refresh() {
        if (!allowed()) { detach(); return }
        val list = events.values.filter { !it.hidden }
            .sortedWith(compareByDescending<IslandEvent> { it.priority }.thenByDescending { it.seq })
        if (list.isEmpty()) { expandedId = null; hide(); return }
        val exp = expandedId?.let { events[it] }
        if (exp != null && !exp.hidden) presentExpanded(exp)
        else { expandedId = null; presentCompact(list[0], list.getOrNull(1)) }
    }

    private fun presentCompact(p: IslandEvent, s: IslandEvent?) {
        ensureViews()
        val isl = island ?: return
        val g = geo()
        val parts = content.compact(p, s != null)
        val slot = if (parts.wide) ctx.dp(70) else ctx.dp(42)
        val w = (g.holeW + ctx.dp(8) + 2 * slot).toFloat()
        val h = g.compactH.toFloat()
        val l = (g.cx - w / 2f).coerceIn(ctx.dpf(2f), max(ctx.dpf(2f), g.screenW - w - ctx.dpf(2f)))
        isl.setCompact(parts, w.toInt(), g.compactH)
        shownId = p.id; expandedNow = false

        var bRect: RectF? = null
        if (s != null) {
            val bl = l + w + ctx.dpf(6f)
            bRect = RectF(bl, g.top.toFloat(), bl + h, g.top + h)
            bubble?.setContent(content.compact(s, true).lead)
            bubbleId = s.id
        } else bubbleId = null

        transition(g, floatArrayOf(l, g.top.toFloat(), w, h, h / 2f, 0f, 1f), bRect)
    }

    private fun presentExpanded(e: IslandEvent) {
        ensureViews()
        val isl = island ?: return
        val g = geo()
        val w = min(g.screenW - ctx.dp(16), ctx.dp(366))
        val l = (g.cx - w / 2f).coerceIn(ctx.dpf(8f), max(ctx.dpf(8f), g.screenW - ctx.dpf(8f) - w))
        val view = content.expanded(e, w, g.compactH) { a -> runAction(e, a) }
        view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val h = max(view.measuredHeight, g.compactH + ctx.dp(64))
        isl.setExpanded(view, w, h)
        shownId = e.id; expandedNow = true; bubbleId = null
        val r = min(ctx.dpf(theme.expandedRadiusDp), h / 2f)
        transition(g, floatArrayOf(l, g.top.toFloat(), w.toFloat(), h.toFloat(), r, 1f, 1f), null)
    }

    private fun hide() {
        val isl = island ?: return
        if (!attached) return
        val token = ++hideToken
        isl.animateTo(tiny(geo())) { if (token == hideToken && events.isEmpty()) detach() }
        bubble?.visibility = View.GONE
        shownId = null; bubbleId = null; expandedNow = false
    }

    private fun transition(g: Geo, target: FloatArray, bRect: RectF?) {
        hideToken++
        val isl = island ?: return
        if (!attached) isl.snapTo(tiny(g))
        val cur = isl.current()
        val u = RectF(cur[0], cur[1], cur[0] + cur[2], cur[1] + cur[3])
        u.union(target[0], target[1], target[0] + target[2], target[1] + target[3])
        bRect?.let { u.union(it) }
        u.inset(-ctx.dpf(18f), -ctx.dpf(18f))
        setWindow(u)
        attach()

        val b = bubble
        if (b != null) {
            if (bRect != null) {
                val blp = b.layoutParams as FrameLayout.LayoutParams
                blp.width = bRect.width().toInt(); blp.height = bRect.height().toInt()
                b.layoutParams = blp
                placeBubble(bRect)
                if (b.visibility != View.VISIBLE) {
                    b.visibility = View.VISIBLE
                    b.scaleX = 0f; b.scaleY = 0f
                    b.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(OvershootInterpolator(2.4f)).start()
                }
            } else if (b.visibility == View.VISIBLE) {
                b.animate().scaleX(0f).scaleY(0f).setDuration(160).withEndAction { b.visibility = View.GONE }.start()
            }
        }
        isl.animateTo(target) { tighten(target, bRect) }
    }

    private fun tighten(target: FloatArray, bRect: RectF?) {
        val u = RectF(target[0], target[1], target[0] + target[2], target[1] + target[3])
        bRect?.let { u.union(it) }
        u.inset(-ctx.dpf(4f), -ctx.dpf(4f))
        setWindow(u)
        bRect?.let { placeBubble(it) }
    }

    private fun placeBubble(r: RectF) {
        bubble?.translationX = r.left - winRect.left
        bubble?.translationY = r.top - winRect.top
    }

    // ------------------------------------------------------------------ window plumbing

    private fun ensureViews() {
        if (root != null) return
        val r = FrameLayout(ctx)
        val isl = IslandView(ctx, theme).also { it.cb = this }
        val b = BubbleView(ctx, theme)
        b.visibility = View.GONE
        b.setOnClickListener { bubbleId?.let { id -> events[id]?.let { e -> e.seq = ++seq; refresh() } } }
        b.setOnLongClickListener {
            bubbleId?.let { id -> expandedId = id; haptic(); refresh() }
            true
        }
        r.addView(isl, FrameLayout.LayoutParams(1, 1))
        r.addView(b, FrameLayout.LayoutParams(1, 1))
        r.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE && expandedNow) collapse()
            false
        }
        root = r; island = isl; bubble = b
        lp = WindowManager.LayoutParams(
            1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "Islandroid"
        }
    }

    private fun setWindow(u: RectF) {
        val p = lp ?: return
        u.roundOut(winRect)
        p.x = winRect.left; p.y = winRect.top
        p.width = winRect.width(); p.height = winRect.height()
        if (attached) root?.let { try { wm.updateViewLayout(it, p) } catch (_: Throwable) {} }
        island?.setOrigin(winRect.left, winRect.top)
    }

    private fun attach() {
        if (attached) return
        val r = root ?: return
        try { wm.addView(r, lp); attached = true } catch (_: Throwable) { attached = false }
    }

    private fun detach() {
        hideToken++
        root?.let { if (attached) try { wm.removeViewImmediate(it) } catch (_: Throwable) {} }
        attached = false
        bubble?.visibility = View.GONE
        shownId = null; bubbleId = null; expandedNow = false
    }

    private fun rebuild() {
        detach()
        root = null; island = null; bubble = null
        theme = Theme.of(ctx)
        content = Content(ctx, theme)
        refresh()
    }

    // ------------------------------------------------------------------ interaction

    private fun haptic() {
        if (IslandPrefs.haptics) island?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun collapse() {
        if (expandedId != null) { expandedId = null; refresh() }
    }

    private fun runAction(e: IslandEvent, a: IslandAction) {
        haptic()
        try { a.run() } catch (_: Throwable) {}
        if (e.kind != Kind.MEDIA) collapse()
    }

    private fun open(e: IslandEvent) {
        haptic()
        try {
            val t = e.onTap
            if (t != null) t() else e.pkg?.let { p ->
                ctx.packageManager.getLaunchIntentForPackage(p)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it)
                }
            }
        } catch (_: Throwable) {}
        collapse()
    }

    override fun onTap() { shownId?.let { events[it] }?.let { open(it) } }

    override fun onLongPress() {
        val id = shownId ?: return
        if (!expandedNow) { expandedId = id; haptic(); refresh() }
    }

    /** iOS: swipe sideways to send an activity to the background until it updates again. */
    override fun onFlingX() {
        val e = shownId?.let { events[it] } ?: return
        if (e.kind == Kind.CALL_INCOMING) return
        e.hidden = true
        if (expandedId == e.id) expandedId = null
        refresh()
    }

    override fun onFlingUp() { if (expandedNow) collapse() }
}

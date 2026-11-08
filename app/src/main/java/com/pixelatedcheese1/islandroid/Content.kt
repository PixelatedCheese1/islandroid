package com.pixelatedcheese1.islandroid

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class Parts(val lead: View, val trail: View?, val wide: Boolean)

/** Builds the views for every event kind, in either theme. */
class Content(private val ctx: Context, private val t: Theme) {

    private fun dp(v: Int) = ctx.dp(v)
    private fun dpf(v: Float) = ctx.dpf(v)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // ---------------------------------------------------------------- helpers

    private fun fresh(d: Drawable): Drawable = d.constantState?.newDrawable(ctx.resources)?.mutate() ?: d

    private fun iconOf(e: IslandEvent): Pair<Icon, Int> = when (e.kind) {
        Kind.CALL_INCOMING, Kind.CALL_ONGOING -> Icon.PHONE to t.positive
        Kind.MEDIA -> Icon.NOTE to t.onSurface
        Kind.LIVE -> Icon.TIMER to t.onSurface
        Kind.NOTIFICATION -> Icon.BELL to t.onSurface
        Kind.CHARGING -> Icon.BOLT to t.positive
        Kind.LOW_BATTERY -> Icon.BATTERY to t.danger
        Kind.AUDIO_DEVICE -> Icon.HEADPHONES to t.onSurface
        Kind.RINGER -> (if (e.muted) Icon.BELL_OFF else Icon.BELL) to (if (e.muted) t.danger else t.onSurface)
        Kind.FOCUS -> Icon.MOON to 0xFF8E8CFF.toInt()
        Kind.AIRPLANE -> Icon.AIRPLANE to t.onSurface
        Kind.HOTSPOT -> Icon.HOTSPOT to t.positive
        Kind.UNLOCK -> Icon.UNLOCK to t.positive
        Kind.PRIVACY -> (if (e.title.startsWith("Camera")) Icon.CAMERA else Icon.MIC) to e.accent
        Kind.SCREEN_REC -> Icon.DOT to t.danger
    }

    private fun text(s: CharSequence, sp: Float, color: Int, bold: Boolean = false, lines: Int = 1): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = if (bold) t.titleFont else t.bodyFont
            includeFontPadding = false
            if (lines == 1) isSingleLine = true else maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun marquee(s: CharSequence, sp: Float, color: Int): TextView = text(s, sp, color, true).apply {
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isSelected = true
    }

    private fun badge(icon: Icon, color: Int, sizeDp: Int): ImageView {
        val iv = ImageView(ctx)
        val size = dp(sizeDp)
        if (t.expressive) {
            val pad = dp(5)
            iv.setPadding(pad, pad, pad, pad)
            iv.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((color and 0x00FFFFFF) or 0x33000000)
            }
            iv.setImageDrawable(icon.drawable(color, size - 2 * pad))
        } else {
            iv.setImageDrawable(icon.drawable(color, size))
        }
        iv.layoutParams = ViewGroup.LayoutParams(size, size)
        return iv
    }

    private fun appIcon(e: IslandEvent, sizeDp: Int): View {
        val d = e.icon
        if (d == null) {
            val (ic, c) = iconOf(e)
            return badge(ic, c, sizeDp)
        }
        return ImageView(ctx).apply {
            setImageDrawable(fresh(d))
            layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
            round(dpf(sizeDp * (if (t.expressive) 0.5f else 0.28f)))
        }
    }

    private fun art(e: IslandEvent, sizeDp: Int, radiusDp: Float): View {
        val iv = ImageView(ctx)
        val a = e.art
        if (a != null) {
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(a)
        } else {
            iv.scaleType = ImageView.ScaleType.CENTER
            iv.setBackgroundColor(t.container)
            iv.setImageDrawable(Icon.NOTE.drawable(t.onContainer, dp(sizeDp / 2)))
        }
        iv.layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
        iv.round(dpf(radiusDp))
        return iv
    }

    private fun chrono(e: IslandEvent, color: Int, sp: Float): Chronometer = Chronometer(ctx).apply {
        base = e.chronoBase
        isCountDown = e.countDown
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = t.titleFont
        includeFontPadding = false
        fontFeatureSettings = "tnum"
        start()
    }

    /** Lock that "unlocks" a moment after appearing – our take on the Face ID animation. */
    private fun unlockGlyph(sizeDp: Int): View {
        val s = dp(sizeDp)
        val iv = ImageView(ctx)
        iv.setImageDrawable(Icon.LOCK.drawable(t.onSurface, s))
        iv.layoutParams = ViewGroup.LayoutParams(s, s)
        iv.postDelayed({
            iv.setImageDrawable(Icon.UNLOCK.drawable(t.positive, s))
            iv.scaleX = 0.6f; iv.scaleY = 0.6f
            iv.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(3f)).setDuration(300).start()
        }, 380)
        return iv
    }

    // ---------------------------------------------------------------- compact

    fun compact(e: IslandEvent, minimal: Boolean): Parts {
        val sz = if (t.expressive) 26 else 22
        val lead: View = when (e.kind) {
            Kind.MEDIA -> art(e, sz, if (t.expressive) 13f else 6f)
            Kind.LIVE, Kind.NOTIFICATION -> appIcon(e, sz)
            Kind.UNLOCK -> unlockGlyph(sz)
            else -> { val (ic, c) = iconOf(e); badge(ic, c, sz) }
        }
        if (minimal) return Parts(lead, null, false)

        fun wave(color: Int, on: Boolean) = WaveformView(ctx, color, on).apply { layoutParams = ViewGroup.LayoutParams(dp(22), dp(18)) }
        fun pct(v: Int, color: Int): View? = if (v >= 0) text("$v%", 13f, color, true) else null

        val trail: View? = when (e.kind) {
            Kind.MEDIA -> wave(e.accent, e.playing)
            Kind.CALL_INCOMING -> wave(t.positive, true)
            Kind.CALL_ONGOING -> if (e.chronoBase != 0L) chrono(e, t.positive, 13f) else null
            Kind.LIVE -> when {
                e.chronoBase != 0L -> chrono(e, e.accent, 13f)
                e.progress >= 0f -> RingView(ctx, e.progress, e.accent).apply { layoutParams = ViewGroup.LayoutParams(dp(20), dp(20)) }
                e.subtitle.isNotEmpty() && e.subtitle.length <= 7 -> text(e.subtitle, 13f, t.onSurface, true)
                else -> null
            }
            Kind.CHARGING -> pct(e.level, t.positive)
            Kind.LOW_BATTERY -> pct(e.level, t.danger)
            Kind.AUDIO_DEVICE -> pct(e.level, t.onSurface)
            Kind.SCREEN_REC -> if (e.chronoBase != 0L) chrono(e, t.danger, 13f) else null
            else -> null
        }
        return Parts(lead, trail, trail is TextView)
    }

    // ---------------------------------------------------------------- expanded

    fun expanded(e: IslandEvent, w: Int, topH: Int, onAction: (IslandAction) -> Unit): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(w, WRAP)
        }

        // Top row sits beside the camera, same as the compact layout.
        val top = FrameLayout(ctx)
        val parts = compact(e, false)
        val lead = if (e.kind == Kind.MEDIA) appIcon(e, 20) else parts.lead
        top.addPlaced(lead, Gravity.START or Gravity.CENTER_VERTICAL, dp(20), 0)
        parts.trail?.let { top.addPlaced(it, Gravity.END or Gravity.CENTER_VERTICAL, 0, dp(20)) }
        col.addView(top, LinearLayout.LayoutParams(w, topH))

        val body: View = when (e.kind) {
            Kind.MEDIA -> bodyMedia(e, onAction)
            Kind.CALL_INCOMING, Kind.CALL_ONGOING -> bodyCall(e, onAction)
            Kind.LIVE, Kind.SCREEN_REC -> bodyLive(e, onAction)
            Kind.NOTIFICATION -> bodyNotif(e, onAction)
            else -> bodyAlert(e)
        }
        col.addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        return col
    }

    private fun bodyBox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(2), dp(22), dp(20))
    }

    private fun bodyMedia(e: IslandEvent, onAction: (IslandAction) -> Unit): View {
        val box = bodyBox()
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val artSize = if (t.expressive) 72 else 60
        head.addView(art(e, artSize, if (t.expressive) 22f else 12f), LinearLayout.LayoutParams(dp(artSize), dp(artSize)))
        val names = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        names.addView(marquee(e.title, t.titleSp, t.onSurface))
        names.addView(text(e.subtitle, t.bodySp, t.onSurfaceVariant), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(3) })
        head.addView(names, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
        box.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val pos = e.position
        if (e.duration > 0 && pos != null) {
            val dur = e.duration
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            fun tick(p: () -> String) = TickerText(ctx, p).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setTextColor(t.onSurfaceVariant); typeface = t.bodyFont
                includeFontPadding = false; fontFeatureSettings = "tnum"
            }
            row.addView(tick { fmtTime(pos()) }, LinearLayout.LayoutParams(dp(36), WRAP))
            val color = if (t.expressive) t.primary else t.onSurface
            val track = (t.onSurfaceVariant and 0x00FFFFFF) or 0x55000000
            row.addView(
                ProgressView(ctx, t.expressive, color, track, true, e.playing) { pos().toFloat() / dur },
                LinearLayout.LayoutParams(0, dp(22), 1f).apply { marginStart = dp(6); marginEnd = dp(6) }
            )
            row.addView(tick { "-" + fmtTime(dur - pos()) }, LinearLayout.LayoutParams(dp(40), WRAP).apply { })
            box.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        }

        if (e.actions.size >= 3) {
            val ctrl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val (prev, mid, next) = e.actions
            ctrl.addView(transport(Icon.PREV, 48, 24, null, t.onSurface, prev, onAction))
            val playBg = if (t.expressive) t.primary else t.container
            val playFg = if (t.expressive) t.onPrimary else t.onSurface
            val playBtn = transport(
                if (e.playing) Icon.PAUSE else Icon.PLAY, 64, 30, playBg, playFg, mid, onAction,
                morph = t.expressive, playing = e.playing
            )
            ctrl.addView(playBtn, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(22); marginEnd = dp(22) })
            ctrl.addView(transport(Icon.NEXT, 48, 24, null, t.onSurface, next, onAction))
            box.addView(ctrl, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        }
        return box
    }

    private fun transport(
        icon: Icon, sizeDp: Int, iconDp: Int, bgColor: Int?, fg: Int,
        a: IslandAction, onAction: (IslandAction) -> Unit,
        morph: Boolean = false, playing: Boolean = false
    ): ImageView {
        val iv = ImageView(ctx)
        iv.scaleType = ImageView.ScaleType.CENTER
        iv.setImageDrawable(icon.drawable(fg, dp(iconDp)))
        iv.layoutParams = ViewGroup.LayoutParams(dp(sizeDp), dp(sizeDp))
        if (bgColor != null) {
            val g = GradientDrawable()
            g.setColor(bgColor)
            val circle = dpf(sizeDp / 2f)
            // Expressive: playing = squircle, paused = circle (the play/pause button "morphs").
            val rest = if (morph && playing) dpf(sizeDp * 0.32f) else circle
            g.cornerRadius = rest
            iv.background = g
            if (morph) {
                iv.setOnTouchListener { _, ev ->
                    val to = when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> dpf(sizeDp * 0.18f)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> rest
                        else -> null
                    }
                    if (to != null) {
                        ValueAnimator.ofFloat(g.cornerRadius, to).apply {
                            duration = 220
                            interpolator = OvershootInterpolator(2.2f)
                            addUpdateListener { g.cornerRadius = it.animatedValue as Float }
                        }.start()
                    }
                    false
                }
            } else iv.pressable(0.9f)
        } else iv.pressable(0.85f)
        iv.isClickable = true
        iv.setOnClickListener { onAction(a) }
        return iv
    }

    private fun capsule(a: IslandAction, onAction: (IslandAction) -> Unit): View {
        val (bgc, fgc) = when (a.style) {
            IslandAction.Style.DANGER -> t.danger to t.onDanger
            IslandAction.Style.POSITIVE -> t.positive to t.onPositive
            IslandAction.Style.PRIMARY -> t.primary to t.onPrimary
            IslandAction.Style.NEUTRAL -> t.container to t.onContainer
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            minimumHeight = dp(46)
            background = GradientDrawable().apply { setColor(bgc); cornerRadius = dpf(if (t.expressive) 20f else 30f) }
            isClickable = true
            setOnClickListener { onAction(a) }
            pressable(0.95f)
        }
        a.icon?.let { ic ->
            val iv = ImageView(ctx)
            iv.setImageDrawable(ic.drawable(fgc, dp(18)))
            if (a.style == IslandAction.Style.DANGER && ic == Icon.PHONE) iv.rotation = 135f
            row.addView(iv, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) })
        }
        row.addView(text(a.label, 14f, fgc, true))
        return row
    }

    private fun actionRow(actions: List<IslandAction>, onAction: (IslandAction) -> Unit): View? {
        if (actions.isEmpty()) return null
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        actions.take(3).forEachIndexed { i, a ->
            row.addView(capsule(a, onAction), LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(10) })
        }
        return row
    }

    private fun avatar(e: IslandEvent, sizeDp: Int): View {
        val s = dp(sizeDp)
        val a = e.art
        if (a != null) {
            return ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(a)
                layoutParams = ViewGroup.LayoutParams(s, s)
                round(s / 2f)
            }
        }
        return TextView(ctx).apply {
            text = e.title.trim().take(1).uppercase().ifEmpty { "?" }
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(t.onContainer)
            typeface = t.titleFont
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(t.container) }
            layoutParams = ViewGroup.LayoutParams(s, s)
        }
    }

    private fun bodyCall(e: IslandEvent, onAction: (IslandAction) -> Unit): View {
        val box = bodyBox()
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(avatar(e, 52), LinearLayout.LayoutParams(dp(52), dp(52)))
        val names = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        names.addView(text(e.title.ifEmpty { "Unknown" }, t.titleSp + 3f, t.onSurface, true))
        val sub: View = if (e.kind == Kind.CALL_ONGOING && e.chronoBase != 0L) chrono(e, t.positive, t.bodySp)
        else text(e.subtitle.ifEmpty { if (e.kind == Kind.CALL_INCOMING) "Incoming call" else "Call" }, t.bodySp, t.onSurfaceVariant)
        names.addView(sub, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(3) })
        head.addView(names, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
        box.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
        actionRow(e.actions, onAction)?.let { box.addView(it, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }) }
        return box
    }

    private fun bodyLive(e: IslandEvent, onAction: (IslandAction) -> Unit): View {
        val box = bodyBox()
        if (e.title.isNotEmpty()) box.addView(text(e.title, t.titleSp, t.onSurface, true))
        if (e.subtitle.isNotEmpty()) box.addView(
            text(e.subtitle, t.bodySp, t.onSurfaceVariant, false, 2),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(3) }
        )
        if (e.chronoBase != 0L) {
            box.addView(chrono(e, e.accent, 34f), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) })
        }
        if (e.progress >= 0f) {
            val track = (t.onSurfaceVariant and 0x00FFFFFF) or 0x55000000
            val p = e.progress
            box.addView(
                ProgressView(ctx, t.expressive, e.accent, track, false, false) { p },
                LinearLayout.LayoutParams(MATCH, dp(22)).apply { topMargin = dp(10) }
            )
        }
        actionRow(e.actions, onAction)?.let { box.addView(it, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) }) }
        return box
    }

    private fun bodyNotif(e: IslandEvent, onAction: (IslandAction) -> Unit): View {
        val box = bodyBox()
        box.addView(text(e.title, t.titleSp, t.onSurface, true))
        if (e.subtitle.isNotEmpty()) box.addView(
            text(e.subtitle, t.bodySp, t.onSurfaceVariant, false, 2),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) }
        )
        actionRow(e.actions, onAction)?.let { box.addView(it, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }) }
        return box
    }

    /** Charging, earbuds, ringer, focus, airplane, hotspot, privacy … big glyph + title + optional level. */
    private fun bodyAlert(e: IslandEvent): View {
        val box = bodyBox()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val (ic, c) = iconOf(e)
        val big = FrameLayout(ctx)
        big.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (t.expressive) (c and 0x00FFFFFF) or 0x33000000 else t.container)
        }
        val g = ImageView(ctx).apply { setImageDrawable(ic.drawable(c, dp(26))); layoutParams = ViewGroup.LayoutParams(dp(26), dp(26)) }
        big.addPlaced(g, Gravity.CENTER)
        // little "pop" so the glyph feels alive (bell swings, bolt pulses …)
        g.scaleX = 0.5f; g.scaleY = 0.5f
        g.animate().scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(380).setInterpolator(OvershootInterpolator(3.5f)).start()
        if (e.kind == Kind.RINGER) { g.rotation = -25f; g.animate().rotation(0f).setStartDelay(120).setDuration(600).setInterpolator(OvershootInterpolator(5f)).start() }
        row.addView(big, LinearLayout.LayoutParams(dp(52), dp(52)))

        val names = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        names.addView(text(e.title, t.titleSp + 1f, t.onSurface, true))
        if (e.subtitle.isNotEmpty()) names.addView(text(e.subtitle, t.bodySp, t.onSurfaceVariant), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(3) })
        row.addView(names, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(14) })
        if (e.level >= 0) row.addView(text("${e.level}%", 24f, c, true))
        box.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))

        if (e.level >= 0) {
            val track = (t.onSurfaceVariant and 0x00FFFFFF) or 0x55000000
            val f = e.level / 100f
            box.addView(
                ProgressView(ctx, false, c, track, false, false) { f },
                LinearLayout.LayoutParams(MATCH, dp(14)).apply { topMargin = dp(12) }
            )
        }
        return box
    }
}

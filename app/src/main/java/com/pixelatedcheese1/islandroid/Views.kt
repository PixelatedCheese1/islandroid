package com.pixelatedcheese1.islandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Little animated equaliser bars (compact trailing slot for music / calls). */
class WaveformView(ctx: Context, private val color: Int, private val active: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val phase = floatArrayOf(0f, 1.7f, 0.6f, 2.4f)

    override fun onDraw(c: Canvas) {
        p.color = color
        val n = 4
        val barW = width / (n + (n - 1) * 0.7f)
        val gap = barW * 0.7f
        val t = SystemClock.uptimeMillis() * 0.007f
        for (i in 0 until n) {
            val amp = if (active) 0.3f + 0.7f * abs(sin(t * (0.8f + 0.28f * i) + phase[i])) else 0.22f
            val h = max(barW, height * amp)
            val x = i * (barW + gap)
            val y = (height - h) / 2f
            c.drawRoundRect(x, y, x + barW, y + h, barW / 2f, barW / 2f, p)
        }
        if (active) postInvalidateOnAnimation()
    }
}

/** Thin circular progress (compact trailing slot for live activities with a progress value). */
class RingView(ctx: Context, private val fraction: Float, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val sw = width * 0.14f
        p.strokeWidth = sw
        r.set(sw / 2, sw / 2, width - sw / 2, height - sw / 2)
        p.color = (color and 0x00FFFFFF) or 0x44000000
        c.drawArc(r, 0f, 360f, false, p)
        p.color = color
        c.drawArc(r, -90f, 360f * fraction.coerceIn(0f, 1f), false, p)
    }
}

/**
 * Progress / scrubber.  iOS: thin capsule.  Material 3 Expressive: wavy active segment with a handle.
 */
class ProgressView(
    ctx: Context,
    private val wavy: Boolean,
    private val color: Int,
    private val track: Int,
    private val animated: Boolean,
    private val playing: Boolean,
    private val fraction: () -> Float
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val sw = (if (wavy) 4f else 4f) * d
        p.strokeWidth = sw
        val x0 = sw / 2f
        val x1 = width - sw / 2f
        val mid = height / 2f
        val f = fraction().coerceIn(0f, 1f)
        val xp = x0 + (x1 - x0) * f
        val gap = if (wavy) 6f * d else 0f

        p.color = track
        if (xp + gap < x1) c.drawLine(xp + gap, mid, x1, mid, p)

        p.color = color
        if (wavy) {
            val amp = if (playing) 2.6f * d else 0f
            val wl = 22f * d
            val shift = if (playing) SystemClock.uptimeMillis() * 0.012f * d else 0f
            path.reset()
            var x = x0
            path.moveTo(x, mid + amp * sin(((x - shift) / wl * 2 * PI).toFloat()))
            while (x < xp) {
                x = min(xp, x + 2f * d)
                path.lineTo(x, mid + amp * sin(((x - shift) / wl * 2 * PI).toFloat()))
            }
            c.drawPath(path, p)
            c.drawLine(xp, mid - 9f * d, xp, mid + 9f * d, p)       // expressive handle
        } else {
            c.drawLine(x0, mid, xp, mid, p)
        }
        if (animated) {
            if (wavy && playing) postInvalidateOnAnimation() else postInvalidateDelayed(500)
        }
    }
}

/** TextView that re-evaluates [provider] twice a second while attached. */
class TickerText(ctx: Context, private val provider: () -> String) : TextView(ctx) {
    private val tick = object : Runnable {
        override fun run() {
            text = provider()
            postDelayed(this, 500)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick)
        tick.run()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}

/** The detached circle that appears beside the island when two things are happening at once. */
class BubbleView(ctx: Context, theme: Theme) : FrameLayout(ctx) {
    init {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(theme.surface) }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        isClickable = true
        isLongClickable = true
    }

    fun setContent(v: View) {
        removeAllViews()
        addPlaced(v, Gravity.CENTER)
    }
}

/**
 * The island body.  Position/size/corner/expansion are driven by ONE spring (0 → 1) that interpolates between
 * the previous and the next target, so any state can morph into any other state (and interrupt itself) smoothly.
 * cur/from/to = [left, top, width, height, radius, expansion(0..1), alpha]
 */
class IslandView(ctx: Context, private val theme: Theme) : FrameLayout(ctx) {

    interface Callbacks {
        fun onTap()
        fun onLongPress()
        fun onFlingX()
        fun onFlingUp()
    }

    var cb: Callbacks? = null
    val compactLayer = FrameLayout(ctx)
    val expandedLayer = FrameLayout(ctx)

    private val bg = GradientDrawable().apply { setColor(theme.surface) }
    private var originX = 0
    private var originY = 0
    private var cur = FloatArray(7)
    private var from = FloatArray(7)
    private var to = FloatArray(7)
    private var frac = 1f
    private var anim: SpringAnimation? = null
    private var onEnd: (() -> Unit)? = null
    private var radius = 0f
    private var blurC = false
    private var blurE = false

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { cb?.onTap(); return true }
        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            cb?.onLongPress()
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > abs(vy) && abs(vx) > 900f) { cb?.onFlingX(); return true }
            if (vy < -900f) { cb?.onFlingUp(); return true }
            return false
        }
    })

    init {
        background = bg
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        addView(compactLayer, LayoutParams(1, 1, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        addView(expandedLayer, LayoutParams(1, 1, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        layoutParams = ViewGroup.LayoutParams(1, 1)
    }

    fun setOrigin(x: Int, y: Int) { originX = x; originY = y; apply() }

    fun current(): FloatArray = cur.copyOf()

    fun setCompact(parts: Parts, w: Int, h: Int) {
        compactLayer.removeAllViews()
        compactLayer.layoutParams = LayoutParams(w, h, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        val m = context.dp(13)
        compactLayer.addPlaced(parts.lead, Gravity.START or Gravity.CENTER_VERTICAL, m, 0)
        parts.trail?.let { compactLayer.addPlaced(it, Gravity.END or Gravity.CENTER_VERTICAL, 0, m) }
    }

    fun setExpanded(v: View, w: Int, h: Int) {
        expandedLayer.removeAllViews()
        expandedLayer.layoutParams = LayoutParams(w, h, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        expandedLayer.addView(v)
    }

    fun snapTo(t: FloatArray) {
        anim?.cancel()
        cur = t.copyOf(); from = t.copyOf(); to = t.copyOf(); frac = 1f
        apply()
    }

    fun animateTo(t: FloatArray, end: (() -> Unit)? = null) {
        anim?.cancel()
        from = cur.copyOf()
        to = t.copyOf()
        onEnd = end
        frac = 0f
        val sa = SpringAnimation(this, FRAC, 1f)
        sa.setStartValue(0f)
        sa.spring.dampingRatio = theme.damping
        sa.spring.stiffness = theme.stiffness
        sa.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
        sa.addEndListener { _, canceled, _, _ ->
            if (!canceled && anim === sa) {
                setFrac(1f)
                onEnd?.invoke()
            }
        }
        anim = sa
        sa.start()
    }

    fun setFrac(v: Float) {
        frac = v
        for (i in 0 until 7) cur[i] = from[i] + (to[i] - from[i]) * v
        apply()
    }

    private fun apply() {
        val w = max(1, cur[2].toInt())
        val h = max(1, cur[3].toInt())
        val lp = layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w; lp.height = h
            layoutParams = lp
        }
        translationX = cur[0] - originX
        translationY = cur[1] - originY
        radius = max(0f, min(cur[4], min(w, h) / 2f))
        bg.cornerRadius = radius
        invalidateOutline()
        alpha = cur[6].coerceIn(0f, 1f)

        val e = cur[5].coerceIn(0f, 1f)
        val ca = (1f - e * 2.4f).coerceIn(0f, 1f)
        val ea = ((e - 0.35f) * 2.2f).coerceIn(0f, 1f)
        compactLayer.alpha = ca
        expandedLayer.alpha = ea
        setVis(compactLayer, ca > 0.01f)
        setVis(expandedLayer, ea > 0.01f)
        blurC = blur(compactLayer, ca, blurC)
        blurE = blur(expandedLayer, ea, blurE)
    }

    private fun setVis(v: View, on: Boolean) {
        val want = if (on) VISIBLE else INVISIBLE
        if (v.visibility != want) v.visibility = want
    }

    private fun blur(v: View, a: Float, was: Boolean): Boolean {
        if (!theme.blur || Build.VERSION.SDK_INT < 31) return false
        val r = (1f - a) * 18f
        return if (r >= 0.5f && a > 0.01f) {
            v.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)); true
        } else {
            if (was) v.setRenderEffect(null)
            false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        pivotX = w / 2f
        pivotY = h / 2f
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        gd.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.97f).scaleY(0.97f).setDuration(110).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(OvershootInterpolator(2f)).start()
        }
        return true
    }

    companion object {
        val FRAC = object : FloatPropertyCompat<IslandView>("frac") {
            override fun getValue(o: IslandView): Float = o.frac
            override fun setValue(o: IslandView, value: Float) { o.setFrac(value) }
        }
    }
}

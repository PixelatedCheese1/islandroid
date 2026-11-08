package com.pixelatedcheese1.islandroid

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.PathParser

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/** Send a PendingIntent in a way that is allowed to start activities from our overlay. */
fun PendingIntent.fire(ctx: Context) {
    try {
        val opts = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else null
        send(ctx, 0, null, null, null, null, opts)
    } catch (_: Throwable) {
    }
}

fun View.round(radius: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
    clipToOutline = true
}

fun View.pressable(scale: Float = 0.92f) {
    setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(OvershootInterpolator(2.5f)).start()
        }
        false
    }
}

/** Adds [v] to this FrameLayout keeping any fixed size it already declared. */
fun FrameLayout.addPlaced(v: View, gravity: Int, ms: Int = 0, me: Int = 0) {
    val o = v.layoutParams
    val lp = FrameLayout.LayoutParams(
        o?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
        o?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity
    )
    lp.marginStart = ms
    lp.marginEnd = me
    addView(v, lp)
}

fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0L)) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Vector glyphs (24x24 viewport, Material-style) drawn from path data so no XML assets are needed. */
enum class Icon(val path: String, val slash: Boolean = false) {
    BOLT("M13,2L5,14h6l-1,8 8,-12h-6z"),
    BATTERY("M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.34C7,21.4 7.6,22 8.33,22h7.34c0.74,0 1.33,-0.6 1.33,-1.33V5.33C17,4.6 16.4,4 15.67,4z"),
    PHONE("M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z"),
    BELL("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"),
    BELL_OFF("M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z", true),
    MOON("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4 -0.44,-0.06 -0.9,-0.1 -1.36,-0.1z"),
    AIRPLANE("M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z"),
    HEADPHONES("M12,1c-4.97,0 -9,4.03 -9,9v7c0,1.66 1.34,3 3,3h3v-8H5v-2c0,-3.87 3.13,-7 7,-7s7,3.13 7,7v2h-4v8h3c1.66,0 3,-1.34 3,-3v-7c0,-4.97 -4.03,-9 -9,-9z"),
    LOCK("M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"),
    UNLOCK("M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1L17,6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2L6,8c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L20,10c0,-1.1 -0.9,-2 -2,-2zM18,20L6,20L6,10h12v10z"),
    MIC("M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"),
    CAMERA("M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z"),
    DOT("M4,12a8,8 0 1,0 16,0a8,8 0 1,0 -16,0z"),
    HOTSPOT("M12,11c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,13c0,-3.31 -2.69,-6 -6,-6s-6,2.69 -6,6c0,2.22 1.21,4.15 3,5.19l1,-1.74c-1.19,-0.7 -2,-1.97 -2,-3.45 0,-2.21 1.79,-4 4,-4s4,1.79 4,4c0,1.48 -0.81,2.75 -2,3.45l1,1.74c1.79,-1.04 3,-2.97 3,-5.19zM12,3C5.93,3 1,7.93 1,14c0,3.64 1.78,6.86 4.5,8.87l1,-1.73C4.58,19.57 3,16.97 3,14c0,-4.97 4.03,-9 9,-9s9,4.03 9,9c0,2.97 -1.58,5.57 -3.5,7.14l1,1.73C21.22,20.86 23,17.64 23,14c0,-6.07 -4.93,-11 -11,-11z"),
    PLAY("M8,5v14l11,-7z"),
    PAUSE("M6,19h4V5H6v14zM14,5v14h4V5h-4z"),
    NEXT("M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"),
    PREV("M6,6h2v12H6zM9.5,12l8.5,6V6z"),
    NOTE("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"),
    TIMER("M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"),
    NAV("M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z"),
    STOP("M6,6h12v12H6z");

    fun drawable(color: Int, sizePx: Int): Drawable = Glyph(this, color, sizePx)
}

class Glyph(private val icon: Icon, color: Int, private val size: Int) : Drawable() {
    private val path: Path = PathParser.createPathFromPathData(icon.path)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var a = 255
    var tint: Int = color

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(b.width() / 24f, b.height() / 24f)
        paint.color = tint
        paint.alpha = Color.alpha(tint) * a / 255
        paint.style = Paint.Style.FILL
        c.drawPath(path, paint)
        if (icon.slash) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.strokeCap = Paint.Cap.ROUND
            c.drawLine(4f, 4f, 20f, 20f, paint)
        }
        c.restore()
    }

    override fun setAlpha(alpha: Int) { a = alpha }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = size
    override fun getIntrinsicHeight(): Int = size
}

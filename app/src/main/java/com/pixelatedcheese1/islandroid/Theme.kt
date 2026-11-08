package com.pixelatedcheese1.islandroid

import android.content.Context
import android.graphics.Typeface
import android.os.Build

/** Everything that differs between the iOS look and the Material 3 Expressive look. */
class Theme(
    val id: Int,
    val surface: Int, val onSurface: Int, val onSurfaceVariant: Int,
    val primary: Int, val onPrimary: Int,
    val container: Int, val onContainer: Int,
    val positive: Int, val onPositive: Int,
    val danger: Int, val onDanger: Int,
    val expandedRadiusDp: Float,
    val damping: Float, val stiffness: Float,
    val blur: Boolean, val expressive: Boolean,
    val titleSp: Float, val bodySp: Float,
    val titleFont: Typeface, val bodyFont: Typeface
) {
    companion object {
        fun of(ctx: Context): Theme = if (IslandPrefs.theme == 0) ios() else material(ctx)

        /** Pure black, white type, snappy spring with a tiny overshoot, blur-in crossfade. */
        private fun ios() = Theme(
            id = 0,
            surface = 0xFF000000.toInt(), onSurface = 0xFFFFFFFF.toInt(), onSurfaceVariant = 0xFF8E8E93.toInt(),
            primary = 0xFFFFFFFF.toInt(), onPrimary = 0xFF000000.toInt(),
            container = 0xFF2C2C2E.toInt(), onContainer = 0xFFFFFFFF.toInt(),
            positive = 0xFF30D158.toInt(), onPositive = 0xFFFFFFFF.toInt(),
            danger = 0xFFFF453A.toInt(), onDanger = 0xFFFFFFFF.toInt(),
            expandedRadiusDp = 44f,
            damping = 0.74f, stiffness = 340f,
            blur = true, expressive = false,
            titleSp = 15f, bodySp = 13f,
            titleFont = Typeface.create("sans-serif-medium", Typeface.NORMAL),
            bodyFont = Typeface.create("sans-serif", Typeface.NORMAL)
        )

        /** Wallpaper-tinted surface (Material You), big rounded shapes, bouncier "expressive" spring,
         *  wavy progress, shape-morphing buttons, filled tonal badges. */
        private fun material(ctx: Context): Theme {
            fun sys(res: Int, fallback: Int): Int =
                if (Build.VERSION.SDK_INT >= 31) ctx.getColor(res) else fallback
            return Theme(
                id = 1,
                surface = sys(android.R.color.system_neutral1_900, 0xFF1D1B20.toInt()),
                onSurface = sys(android.R.color.system_neutral1_100, 0xFFE6E0E9.toInt()),
                onSurfaceVariant = sys(android.R.color.system_neutral2_200, 0xFFCAC4D0.toInt()),
                primary = sys(android.R.color.system_accent1_200, 0xFFD0BCFF.toInt()),
                onPrimary = sys(android.R.color.system_accent1_800, 0xFF381E72.toInt()),
                container = sys(android.R.color.system_accent2_700, 0xFF4A4458.toInt()),
                onContainer = sys(android.R.color.system_accent2_100, 0xFFE8DEF8.toInt()),
                positive = 0xFF6DD58C.toInt(), onPositive = 0xFF00391A.toInt(),
                danger = 0xFFFFB4AB.toInt(), onDanger = 0xFF690005.toInt(),
                expandedRadiusDp = 32f,
                damping = 0.52f, stiffness = 230f,
                blur = false, expressive = true,
                titleSp = 17f, bodySp = 14f,
                titleFont = Typeface.create("sans-serif-medium", Typeface.NORMAL),
                bodyFont = Typeface.create("sans-serif", Typeface.NORMAL)
            )
        }
    }
}

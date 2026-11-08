package com.pixelatedcheese1.islandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {

    private lateinit var col: LinearLayout
    private lateinit var statusBox: LinearLayout
    private lateinit var cutoutText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandPrefs.init(this)

        val sv = ScrollView(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(32)) }
        sv.addView(col)
        setContentView(sv)
        sv.setOnApplyWindowInsetsListener { v, ins ->
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            ins
        }

        title("Islandroid", 30f)
        body("A Dynamic Island for Android. It wraps your front camera and shows live activities, calls, music and system alerts.")

        header("Setup")
        statusBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(statusBox)
        body("Sideloaded? Allow restricted settings once (App info → ⋮) and both switches unlock.", 12f)

        header("Camera")
        cutoutText = body("", 14f)

        header("Theme")
        val rg = RadioGroup(this)
        val ios = RadioButton(this).apply { text = "iOS Dynamic Island — pure black, white type, snappy spring, blur-in"; id = 1000 }
        val mat = RadioButton(this).apply { text = "Material 3 Expressive — wallpaper colours, big shapes, wavy progress, bouncy spring"; id = 1001 }
        rg.addView(ios); rg.addView(mat)
        rg.check(if (IslandPrefs.theme == 0) 1000 else 1001)
        rg.setOnCheckedChangeListener { _, id -> IslandPrefs.theme = if (id == 1000) 0 else 1; Demo.clearAll(); Demo.media(true) }
        col.addView(rg)

        header("General")
        switchRow("Island enabled", "Master switch", IslandPrefs.master) { IslandPrefs.master = it }
        switchRow("Haptics", "Tick on expand and button presses", IslandPrefs.haptics) { IslandPrefs.haptics = it }

        header("Position fine-tuning")
        body("The island aligns to the camera automatically. Nudge it if your phone reports the cutout slightly off.", 12f)
        seek("Horizontal offset", -40, 40, IslandPrefs.offX) { IslandPrefs.offX = it }
        seek("Vertical offset", -20, 20, IslandPrefs.offY) { IslandPrefs.offY = it }
        seek("Extra width around camera", -6, 40, IslandPrefs.extraW) { IslandPrefs.extraW = it }

        header("What the island shows")
        IslandPrefs.FEATURES.forEach { f -> switchRow(f.title, f.desc, IslandPrefs.feature(f.key)) { IslandPrefs.setFeature(f.key, it) } }

        header("Try it (needs the accessibility service on)")
        val demos = listOf<Pair<String, () -> Unit>>(
            "Music" to { Demo.media(true) }, "Incoming call" to { Demo.call() },
            "Timer" to { Demo.timer() }, "Navigation" to { Demo.navigation() },
            "Charging" to { Demo.charging() }, "Low battery" to { Demo.lowBattery() },
            "Earbuds" to { Demo.earbuds() }, "Silent" to { Demo.silent(true) },
            "Do Not Disturb" to { Demo.dnd() }, "Airplane" to { Demo.airplane() },
            "Hotspot" to { Demo.hotspot() }, "Unlock" to { Demo.unlock() },
            "Camera dot" to { Demo.camera() }, "Screen rec" to { Demo.recording() },
            "Notification" to { Demo.notification() }, "Two activities" to { Demo.split() },
            "Clear all" to { Demo.clearAll() }
        )
        demos.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (label, action) ->
                row.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            col.addView(row)
        }
        body("Gestures: tap = open app · long-press = expand · swipe sideways = send to background · swipe up / tap outside = collapse.", 12f)
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        val r = display?.cutout?.boundingRectTop
        val d = resources.displayMetrics.density
        cutoutText.text = if (r != null && !r.isEmpty)
            "Detected: ${(r.width() / d).toInt()}×${(r.height() / d).toInt()} dp camera cutout, centre x = ${(r.centerX() / d).toInt()} dp. The island wraps it automatically."
        else "No top camera cutout reported by the system. A virtual island at the top centre is used."
    }

    // ------------------------------------------------------------------ status

    private fun a11yOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    /** Tries the service's own page first (Android 14+), falls back to the general list. */
    private fun openAccessibility() {
        val cn = android.content.ComponentName(this, IslandAccessibilityService::class.java).flattenToString()
        try {
            startActivity(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra(Intent.EXTRA_COMPONENT_NAME, cn)
                data = android.net.Uri.parse("package:$packageName")
            })
        } catch (_: Throwable) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun renderStatus() {
        statusBox.removeAllViews()
        statusRow("Accessibility service — draws the island", a11yOn(), "Open") {
            openAccessibility()
        }
        if (!a11yOn()) statusRow("Switch greyed out? Allow restricted settings (App info → ⋮)", false, "App info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
        }
        statusRow("Notification access — music, calls, timers, navigation",
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName), "Open") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        val bt = Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        statusRow("Bluetooth — earbud connect cards (optional)", bt, "Grant") {
            if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        renderStatus()
    }

    private fun statusRow(label: String, ok: Boolean, action: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = (if (ok) "✅ " else "⚠️ ") + label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (!ok) row.addView(Button(this).apply { text = action; isAllCaps = false; setOnClickListener { onClick() } })
        statusBox.addView(row)
    }

    // ------------------------------------------------------------------ ui helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun title(s: String, sp: Float) {
        col.addView(TextView(this).apply { text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); typeface = Typeface.DEFAULT_BOLD })
    }

    private fun header(s: String) {
        col.addView(TextView(this).apply {
            text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(22), 0, dp(6))
        })
    }

    private fun body(s: String, sp: Float = 14f): TextView {
        val tv = TextView(this).apply { text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); alpha = 0.8f; setPadding(0, dp(2), 0, dp(4)) }
        col.addView(tv)
        return tv
    }

    private fun switchRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val sw = Switch(this).apply {
            text = label; isChecked = checked
            setOnCheckedChangeListener { _: CompoundButton, v: Boolean -> onChange(v) }
        }
        box.addView(sw)
        box.addView(TextView(this).apply { text = desc; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); alpha = 0.65f })
        col.addView(box)
    }

    private fun seek(label: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply { text = "$label: $value dp" }
        val sb = SeekBar(this).apply {
            this.max = max - min; progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) { tv.text = "$label: ${p + min} dp"; onChange(p + min) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { Demo.timer() }
            })
        }
        col.addView(tv); col.addView(sb)
    }
}

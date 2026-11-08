package com.pixelatedcheese1.islandroid

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Build
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** System-level alerts: the things iOS shows in the island without any app being involved. */
class SystemMonitors(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var lastPlugged: Boolean? = null
    private var lowFired = false
    private var micOn = false
    private val camsOpen = HashSet<String>()
    private var started = false

    private val green = 0xFF30D158.toInt()
    private val orange = 0xFFFF9F0A.toInt()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBattery(i)
                AudioManager.RINGER_MODE_CHANGED_ACTION -> onRinger()
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> onFocus()
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> onAirplane(i)
                BluetoothDevice.ACTION_ACL_CONNECTED -> onBluetooth(i, true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> onBluetooth(i, false)
                Intent.ACTION_USER_PRESENT -> onUnlock()
                "android.net.wifi.WIFI_AP_STATE_CHANGED" -> onHotspot(i)
            }
        }
    }

    private val camCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) { camsOpen.add(cameraId); updatePrivacy() }
        override fun onCameraAvailable(cameraId: String) { camsOpen.remove(cameraId); updatePrivacy() }
    }

    private val micCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            micOn = !configs.isNullOrEmpty()
            updatePrivacy()
        }
    }

    fun start() {
        if (started) return
        started = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        try { cam.registerAvailabilityCallback(camCb, main) } catch (_: Throwable) {}
        try { audio.registerAudioRecordingCallback(micCb, main) } catch (_: Throwable) {}
    }

    fun stop() {
        if (!started) return
        started = false
        try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {}
        try { cam.unregisterAvailabilityCallback(camCb) } catch (_: Throwable) {}
        try { audio.unregisterAudioRecordingCallback(micCb) } catch (_: Throwable) {}
    }

    private fun alert(id: String, kind: Kind, title: String, sub: String = "", ms: Long = 3500, expand: Boolean = true): IslandEvent =
        IslandEvent(id, kind).apply {
            this.title = title; subtitle = sub
            priority = 90; timeoutMs = ms; autoExpand = expand
        }

    // ---------------------------------------------------------------- battery

    private fun onBattery(i: Intent) {
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
        val plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val plugged = plug != 0
        val prev = lastPlugged
        lastPlugged = plugged

        if (prev != null && !prev && plugged) {
            val wireless = plug == BatteryManager.BATTERY_PLUGGED_WIRELESS
            IslandBus.post(alert("charging", Kind.CHARGING, if (wireless) "Wireless Charging" else "Charging",
                if (pct == 100) "Fully charged" else "Battery").apply { level = pct; accent = green })
        }
        if (prev != null && prev && !plugged) IslandBus.remove("charging")
        if (prev != null && !plugged && pct in 0..20 && !lowFired) {
            lowFired = true
            IslandBus.post(alert("lowbat", Kind.LOW_BATTERY, "Low Battery", "Connect a charger", 4000).apply { level = pct })
        }
        if (pct > 20) lowFired = false
    }

    // ---------------------------------------------------------------- ringer / focus / airplane

    private fun onRinger() {
        val m = audio.ringerMode
        val muted = m != AudioManager.RINGER_MODE_NORMAL
        IslandBus.post(alert("ringer", Kind.RINGER,
            when (m) { AudioManager.RINGER_MODE_SILENT -> "Silent"; AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; else -> "Ring" },
            "", 2200).apply { this.muted = muted })
    }

    private fun onFocus() {
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        IslandBus.post(alert("focus", Kind.FOCUS, "Do Not Disturb", if (on) "On" else "Off", 2500))
    }

    private fun onAirplane(i: Intent) {
        val on = i.getBooleanExtra("state", false)
        IslandBus.post(alert("airplane", Kind.AIRPLANE, "Airplane Mode", if (on) "On" else "Off", 2500))
    }

    private fun onUnlock() {
        IslandBus.post(IslandEvent("unlock", Kind.UNLOCK).apply { priority = 95; timeoutMs = 1300 })
    }

    private fun onHotspot(i: Intent) {
        when (i.getIntExtra("wifi_state", 0)) {
            13 -> IslandBus.post(IslandEvent("hotspot", Kind.HOTSPOT).apply {
                title = "Personal Hotspot"; subtitle = "On"; priority = 30
            })
            11, 10 -> IslandBus.remove("hotspot")
        }
    }

    // ---------------------------------------------------------------- bluetooth audio ("AirPods connected")

    private fun onBluetooth(i: Intent, connected: Boolean) {
        if (Build.VERSION.SDK_INT >= 31 &&
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return
        @Suppress("DEPRECATION")
        val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val major = try { d.bluetoothClass?.majorDeviceClass } catch (_: Throwable) { null }
        if (major != BluetoothClass.Device.Major.AUDIO_VIDEO) return
        val name = try { d.name } catch (_: Throwable) { null } ?: "Bluetooth audio"
        if (connected) {
            // give the headset a moment to report its battery
            main.postDelayed({
                IslandBus.post(alert("bt", Kind.AUDIO_DEVICE, name, "Connected", 4000).apply { level = battery(d) })
            }, 1800)
        } else {
            IslandBus.post(alert("bt", Kind.AUDIO_DEVICE, name, "Disconnected", 2200, expand = false))
        }
    }

    private fun battery(d: BluetoothDevice): Int = try {
        val v = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as? Int ?: -1
        if (v in 0..100) v else -1
    } catch (_: Throwable) { -1 }

    // ---------------------------------------------------------------- camera / mic dots

    private fun updatePrivacy() {
        val cameraOn = camsOpen.isNotEmpty()
        if (!cameraOn && !micOn) { IslandBus.remove("privacy"); return }
        IslandBus.post(IslandEvent("privacy", Kind.PRIVACY).apply {
            title = if (cameraOn) "Camera in use" else "Microphone in use"
            accent = if (cameraOn) green else orange
            priority = 10
        })
    }
}

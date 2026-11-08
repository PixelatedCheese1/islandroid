package com.pixelatedcheese1.islandroid

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent

class IslandAccessibilityService : AccessibilityService() {
    private var overlay: IslandOverlay? = null
    private var monitors: SystemMonitors? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandPrefs.init(this)
        overlay = IslandOverlay(this).also { IslandBus.sink = it }
        monitors = SystemMonitors(this).also { it.start() }
        running = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.reconfigure()           // rotation / fold / display-size change -> re-detect the camera
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        running = false
        monitors?.stop(); monitors = null
        overlay?.destroy(); overlay = null
        IslandBus.sink = null
    }

    companion object {
        @Volatile var running = false
    }
}

package com.turbokn.app.core

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.turbokn.app.shizuku.ShizukuShell

/**
 * Watches window changes to learn which package is in the foreground.
 *
 * An accessibility service is used instead of a foreground service because a
 * foreground service must show a persistent notification, and TurboKn is meant
 * to stay silent while a game is running.
 */
class GameWatcherService : AccessibilityService() {

    private var controller: AutoModeController? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pkg == packageName) return

        val previous = ForegroundTracker.current
        if (previous == pkg) return

        ForegroundTracker.current = pkg
        ForegroundTracker.onPackageChanged?.invoke(previous, pkg)
        controller?.onForegroundPackage(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundTracker.connected = true
        // The automatic mode lives here so it survives the app being closed.
        controller = AutoModeController(applicationContext)
        if (ShizukuShell.isBinderAlive && ShizukuShell.isPermissionGranted) ShizukuShell.bind()
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        ForegroundTracker.connected = false
        super.onDestroy()
    }
}

/** Shared, process-wide state for the currently visible package. */
object ForegroundTracker {
    @Volatile
    var current: String? = null

    @Volatile
    var connected: Boolean = false

    /** Invoked as (previousPackage, newPackage) on every foreground change. */
    @Volatile
    var onPackageChanged: ((String?, String) -> Unit)? = null
}

package com.turbokn.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.turbokn.app.shizuku.ShizukuShell

/**
 * Decides when the in-game HUD is visible.
 *
 * The HUD belongs to the game, so it must disappear the moment the user leaves
 * it — not only when the profile is turned off. Two sources feed this: the
 * accessibility service reports foreground changes immediately, and a Shizuku
 * poll covers the manual path, where the user starts a game from the app and
 * the accessibility service may not be enabled.
 */
object OverlayCoordinator {

    private const val POLL_MS = 2000L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var visible = false

    private var watcher: Runnable? = null

    /** Called on every foreground change; [pkg] is the new foreground package. */
    fun onForegroundChanged(context: Context, pkg: String?) {
        val active = BoostState.activePackage
        val shouldShow = BoostState.overlayEnabled &&
            active != null &&
            pkg == active &&
            Settings.canDrawOverlays(context)

        if (shouldShow) {
            show(context, BoostState.activeLabel ?: active ?: return)
        } else {
            hide()
        }
    }

    /** Shows the HUD immediately after the profile is applied to a game. */
    fun showForActiveGame(context: Context) {
        val active = BoostState.activePackage ?: return
        if (!BoostState.overlayEnabled) return
        if (!Settings.canDrawOverlays(context)) return
        show(context, BoostState.activeLabel ?: active)
    }

    fun hide() {
        if (!visible && !GameOverlay.isShowing) return
        handler.post { GameOverlay.hide() }
        visible = false
    }

    /**
     * Polls the foreground package while a profile is active, so a HUD left on
     * screen is removed as soon as the user switches away from the game. The
     * poll stops on its own once no profile is active.
     */
    fun startWatching(context: Context) {
        if (watcher != null) return
        val appContext = context.applicationContext
        val runnable = object : Runnable {
            override fun run() {
                if (BoostState.activePackage != null) {
                    val pkg = foregroundPackage()
                    handler.post { onForegroundChanged(appContext, pkg) }
                    handler.postDelayed(this, POLL_MS)
                } else {
                    hide()
                    watcher = null
                }
            }
        }
        watcher = runnable
        handler.postDelayed(runnable, POLL_MS)
    }

    fun stopWatching() {
        watcher?.let { handler.removeCallbacks(it) }
        watcher = null
    }

    /** Reads the foreground package through Shizuku. */
    fun foregroundPackage(): String? {
        val output = ShizukuShell.exec("dumpsys activity activities")
        if (output.startsWith("error:")) return null
        return Regex("mResumedActivity.*?([A-Za-z0-9_.]+)/")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?: Regex("topResumedActivity.*?([A-Za-z0-9_.]+)/")
                .find(output)
                ?.groupValues
                ?.get(1)
    }

    private fun show(context: Context, label: String) {
        if (visible && GameOverlay.isShowing) {
            GameOverlay.updateLabel(label)
            return
        }
        handler.post { GameOverlay.show(context.applicationContext, label) }
        visible = true
    }
}

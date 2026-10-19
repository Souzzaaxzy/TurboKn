package com.turbokn.app.core

import android.content.Context
import com.turbokn.app.data.InstalledApps
import com.turbokn.app.data.SettingsStore
import com.turbokn.app.shizuku.ShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives automatic mode from inside the accessibility service process, so it
 * keeps working while the app UI is not running.
 */
class AutoModeController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = SettingsStore(context)

    @Volatile
    private var activePackage: String? = null

    @Volatile
    private var zenModeBefore: Int? = null

    private var fpsJob: Job? = null

    fun onForegroundPackage(pkg: String) {
        scope.launch {
            val settings = store.current()
            if (pkg == context.packageName) {
                OverlayCoordinator.onForegroundChanged(context, pkg)
                return@launch
            }
            if (pkg == activePackage) return@launch

            if (activePackage != null) {
                val previous = activePackage!!
                val previousZen = zenModeBefore
                activePackage = null
                zenModeBefore = null
                BoostState.activePackage = null
                BoostState.activeLabel = null
                OverlayCoordinator.hide()
                FpsSampler.reset()
                withContext(Dispatchers.IO) { ProfileEngine.revert(settings, previous, previousZen) }
            }

            if (!settings.autoMode) {
                OverlayCoordinator.onForegroundChanged(context, pkg)
                return@launch
            }

            val isGame = withContext(Dispatchers.IO) { GameActions.isGamePackage(pkg) }
            if (!isGame) {
                OverlayCoordinator.onForegroundChanged(context, pkg)
                return@launch
            }

            if (!ShizukuShell.isReady) {
                if (ShizukuShell.isPermissionGranted) ShizukuShell.bind()
                // Binding is async: give it a moment before declaring Shizuku absent.
                if (!ShizukuShell.awaitReady()) {
                    ShizukuNotifier.notifyShizukuMissing(context)
                    return@launch
                }
            }

            ShizukuNotifier.clear(context)
            withContext(Dispatchers.IO) {
                if (settings.dnd) zenModeBefore = GameActions.zenMode()
                ProfileEngine.apply(settings, pkg)
            }
            activePackage = pkg

            val label = withContext(Dispatchers.IO) {
                InstalledApps.entryFor(context, pkg)?.label ?: pkg
            }
            BoostState.activePackage = pkg
            BoostState.activeLabel = label
            withContext(Dispatchers.Main) {
                OverlayCoordinator.showForActiveGame(context)
                OverlayCoordinator.startWatching(context)
            }
            startFpsSampling(pkg)
        }
    }

    /**
     * Samples the game's frame rate through SurfaceFlinger while the HUD is up.
     */
    private fun startFpsSampling(pkg: String) {
        fpsJob?.cancel()
        fpsJob = scope.launch {
            withContext(Dispatchers.IO) { FpsSampler.start(pkg) }
            while (isActive) {
                val value = withContext(Dispatchers.IO) { FpsSampler.sample(pkg) }
                GameOverlay.updateFps(value)
                delay(FPS_SAMPLE_MS)
            }
        }
    }

    fun stop() {
        val previous = activePackage
        val previousZen = zenModeBefore
        activePackage = null
        zenModeBefore = null
        fpsJob?.cancel()
        fpsJob = null
        OverlayCoordinator.stopWatching()
        BoostState.activePackage = null
        BoostState.activeLabel = null
        FpsSampler.reset()
        GameOverlay.hide()
        if (previous != null) {
            scope.launch {
                val settings = store.current()
                withContext(Dispatchers.IO) { ProfileEngine.revert(settings, previous, previousZen) }
            }
        }
        scope.cancel()
    }

    private companion object {
        const val FPS_SAMPLE_MS = 2000L
    }
}

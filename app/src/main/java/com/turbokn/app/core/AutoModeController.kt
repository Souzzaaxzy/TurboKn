package com.turbokn.app.core

import android.content.Context
import com.turbokn.app.data.SettingsStore
import com.turbokn.app.shizuku.ShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    fun onForegroundPackage(pkg: String) {
        scope.launch {
            val settings = store.current()
            if (!settings.autoMode) return@launch
            if (pkg == context.packageName) return@launch
            if (pkg == activePackage) return@launch

            if (activePackage != null) {
                val previous = activePackage!!
                val previousZen = zenModeBefore
                activePackage = null
                zenModeBefore = null
                withContext(Dispatchers.IO) { ProfileEngine.revert(settings, previous, previousZen) }
            }

            val isGame = withContext(Dispatchers.IO) { GameActions.isGamePackage(pkg) }
            if (!isGame) return@launch

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
        }
    }

    fun stop() {
        val previous = activePackage
        val previousZen = zenModeBefore
        activePackage = null
        zenModeBefore = null
        if (previous != null) {
            scope.launch {
                val settings = store.current()
                withContext(Dispatchers.IO) { ProfileEngine.revert(settings, previous, previousZen) }
            }
        }
        scope.cancel()
    }
}

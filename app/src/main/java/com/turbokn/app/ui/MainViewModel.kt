package com.turbokn.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbokn.app.core.BoostState
import com.turbokn.app.core.DeviceMetrics
import com.turbokn.app.core.FpsSampler
import com.turbokn.app.core.GameActions
import com.turbokn.app.core.GameOverlay
import com.turbokn.app.core.GameWatcherService
import com.turbokn.app.core.MetricsReader
import com.turbokn.app.core.OverlayCoordinator
import com.turbokn.app.core.ProfileEngine
import com.turbokn.app.core.ProfileStep
import com.turbokn.app.core.ShizukuNotifier
import com.turbokn.app.data.AppEntry
import com.turbokn.app.data.InstalledApps
import com.turbokn.app.data.ProfileSettings
import com.turbokn.app.data.SettingsStore
import com.turbokn.app.shizuku.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far the Shizuku bridge has progressed. */
enum class ShizukuState { OFFLINE, PERMISSION_REQUIRED, CONNECTING, READY }

data class UiState(
    val shizuku: ShizukuState = ShizukuState.OFFLINE,
    val apps: List<AppEntry> = emptyList(),
    val recents: List<AppEntry> = emptyList(),
    val selected: AppEntry? = null,
    val downscale: String = "disable",
    val fps: Int = 0,
    val thermalStatus: Int = 0,
    val fixedPerf: Boolean = false,
    val dnd: Boolean = false,
    val overlay: Boolean = true,
    val overlayAllowed: Boolean = false,
    val autoMode: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val active: Boolean = false,
    val activePackage: String? = null,
    val metrics: DeviceMetrics = DeviceMetrics(),
    val log: List<String> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val store = SettingsStore(app)

    /** Captured before DND is enabled so the user's own filter can be restored. */
    private var zenModeBefore: Int? = null

    private var overlayFpsJob: Job? = null

    init {
        restoreSettings()
        refreshShizuku()
        loadApps()
        viewModelScope.launch {
            while (true) {
                refreshShizuku()
                refreshAccessibility()
                refreshOverlayPermission()
                delay(2000)
            }
        }
        viewModelScope.launch {
            while (true) {
                if (_state.value.active) refreshMetrics()
                delay(5000)
            }
        }
    }

    /** Loads the persisted profile so the UI matches what automatic mode uses. */
    private fun restoreSettings() {
        viewModelScope.launch {
            val saved = store.current()
            BoostState.overlayEnabled = saved.overlay
            _state.update {
                it.copy(
                    autoMode = saved.autoMode,
                    downscale = saved.downscale,
                    fps = saved.fps,
                    thermalStatus = saved.thermalStatus,
                    fixedPerf = saved.fixedPerf,
                    dnd = saved.dnd,
                    overlay = saved.overlay,
                )
            }
            val context = getApplication<Application>()
            val recents = withContext(Dispatchers.IO) {
                saved.recentPackages.mapNotNull { InstalledApps.entryFor(context, it) }
            }
            val target = saved.packageName?.let { pkg ->
                recents.firstOrNull { it.packageName == pkg }
                    ?: withContext(Dispatchers.IO) { InstalledApps.entryFor(context, pkg) }
            }
            _state.update { it.copy(recents = recents, selected = target ?: it.selected) }
        }
    }

    fun refreshShizuku() {
        val newState = when {
            !ShizukuShell.isBinderAlive -> ShizukuState.OFFLINE
            !ShizukuShell.isPermissionGranted -> ShizukuState.PERMISSION_REQUIRED
            ShizukuShell.isReady -> ShizukuState.READY
            else -> ShizukuState.CONNECTING
        }
        if (newState != ShizukuState.OFFLINE &&
            newState != ShizukuState.PERMISSION_REQUIRED &&
            _state.value.shizuku != ShizukuState.READY
        ) {
            ShizukuShell.bind()
        }
        _state.update { it.copy(shizuku = newState) }
    }

    private fun refreshAccessibility() {
        _state.update { it.copy(accessibilityEnabled = isAccessibilityEnabled(getApplication())) }
    }

    private fun refreshOverlayPermission() {
        val allowed = Settings.canDrawOverlays(getApplication())
        _state.update { it.copy(overlayAllowed = allowed) }
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, GameWatcherService::class.java).flattenToString()
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return setting.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledApps.launchable(getApplication()) }
            _state.update { it.copy(apps = apps) }
        }
    }

    fun requestShizukuPermission() {
        ShizukuShell.requestPermission()
        viewModelScope.launch {
            delay(500)
            refreshShizuku()
        }
    }

    fun select(entry: AppEntry) {
        _state.update { it.copy(selected = entry) }
        viewModelScope.launch {
            store.setTarget(entry.packageName, entry.label)
            store.pushRecent(entry.packageName)
            refreshRecents()
        }
    }

    /**
     * Tapping a game in the recent strip both selects it and opens it, so the
     * user goes straight into the game with the profile already applied.
     */
    fun openGame(entry: AppEntry) {
        select(entry)
        launchGame(entry)
    }

    private suspend fun refreshRecents() {
        val context = getApplication<Application>()
        val saved = store.current()
        val recents = withContext(Dispatchers.IO) {
            saved.recentPackages.mapNotNull { InstalledApps.entryFor(context, it) }
        }
        _state.update { it.copy(recents = recents) }
    }

    fun setDownscale(value: String) {
        _state.update { it.copy(downscale = value) }
        viewModelScope.launch { store.setDownscale(value) }
    }

    fun setFps(value: Int) {
        _state.update { it.copy(fps = value) }
        viewModelScope.launch { store.setFps(value) }
    }

    fun setThermalStatus(value: Int) {
        _state.update { it.copy(thermalStatus = value) }
        viewModelScope.launch { store.setThermalStatus(value) }
    }

    fun setDnd(enabled: Boolean) {
        _state.update { it.copy(dnd = enabled) }
        viewModelScope.launch { store.setDnd(enabled) }
    }

    fun setFixedPerf(enabled: Boolean) {
        _state.update { it.copy(fixedPerf = enabled) }
        viewModelScope.launch { store.setFixedPerf(enabled) }
        // Apply immediately when a game is already running, so the toggle is
        // not a setting that only takes effect on the next activation.
        BoostState.activePackage ?: return
        if (_state.value.shizuku != ShizukuState.READY) return
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { GameActions.setFixedPerformance(enabled) }
            logStep(ProfileStep("clocks fixos", out, ok = !out.startsWith("error:")))
        }
    }

    fun setOverlay(enabled: Boolean) {
        _state.update { it.copy(overlay = enabled) }
        BoostState.overlayEnabled = enabled
        viewModelScope.launch { store.setOverlay(enabled) }
        if (!enabled) {
            stopOverlay()
            return
        }
        if (_state.value.active) {
            showOverlayIfEnabled()
        }
    }

    fun setAutoMode(enabled: Boolean) {
        _state.update { it.copy(autoMode = enabled) }
        viewModelScope.launch { store.setAutoMode(enabled) }
    }

    fun activate() {
        val s = _state.value
        val target = s.selected ?: return appendLog("Selecione um jogo primeiro.")
        if (s.shizuku != ShizukuState.READY) {
            appendLog("Shizuku indisponível. Abra o app Shizuku e conceda a permissão.")
            return
        }

        viewModelScope.launch {
            appendLog("Ativando modo performance para ${target.label}…")
            val settings = store.current()
            withContext(Dispatchers.IO) { applyProfile(settings, target.packageName) }
            ShizukuNotifier.clear(getApplication())
            _state.update { it.copy(active = true, activePackage = target.packageName) }
            syncBoostState(target.packageName, target.label)
            showOverlayIfEnabled()
            refreshMetrics()
        }
    }

    /**
     * Applies the profile to [target] and opens it, so the user lands directly
     * in the game with the settings already in place. Launching before applying
     * would let the game boot with the stock configuration and the downscale
     * would only take effect on the next start.
     */
    fun launchGame(target: AppEntry) {
        val s = _state.value
        if (s.shizuku != ShizukuState.READY) {
            appendLog("Shizuku indisponível. Abra o app Shizuku e conceda a permissão.")
            return
        }
        if (s.activePackage == target.packageName) {
            viewModelScope.launch { startGame(target) }
            return
        }

        viewModelScope.launch {
            if (s.activePackage != null) {
                val previous = s.activePackage
                val previousZen = zenModeBefore
                zenModeBefore = null
                withContext(Dispatchers.IO) { revertProfile(previous, previousZen) }
            }
            appendLog("Preparando ${target.label}…")
            val settings = store.current()
            withContext(Dispatchers.IO) { applyProfile(settings, target.packageName) }
            ShizukuNotifier.clear(getApplication())
            _state.update { it.copy(active = true, activePackage = target.packageName) }
            syncBoostState(target.packageName, target.label)
            startGame(target)
            showOverlayIfEnabled()
            refreshMetrics()
        }
    }

    private fun showOverlayIfEnabled() {
        if (!BoostState.overlayEnabled) return
        if (!Settings.canDrawOverlays(getApplication())) {
            appendLog("overlay desativado: falta permitir sobrepor outros apps")
            return
        }
        OverlayCoordinator.showForActiveGame(getApplication())
        OverlayCoordinator.startWatching(getApplication())
        FpsSampler.reset(BoostState.activePackage)
        startOverlayFpsLoop()
    }

    /** Feeds the HUD's FPS readout while a profile is active. */
    private fun startOverlayFpsLoop() {
        if (overlayFpsJob != null) return
        val pkg = BoostState.activePackage ?: return
        overlayFpsJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { FpsSampler.start(pkg) }
            while (true) {
                val current = BoostState.activePackage
                if (current == null) break
                val value = withContext(Dispatchers.IO) { FpsSampler.sample(current) }
                GameOverlay.updateFps(value)
                delay(FPS_REFRESH_MS)
            }
            overlayFpsJob = null
        }
    }

    private fun stopOverlay() {
        overlayFpsJob?.cancel()
        overlayFpsJob = null
        OverlayCoordinator.stopWatching()
        FpsSampler.reset()
        GameOverlay.hide()
    }

    private suspend fun startGame(target: AppEntry) {
        val launched = withContext(Dispatchers.IO) {
            InstalledApps.launch(getApplication(), target.packageName)
        }
        appendLog(
            if (launched) "abrindo ${target.label}" else "não foi possível abrir ${target.label}"
        )
    }

    private fun applyProfile(settings: ProfileSettings, pkg: String) {
        zenModeBefore = if (settings.dnd) GameActions.zenMode() else null
        ProfileEngine.apply(settings, pkg).forEach(::logStep)
    }

    /** Surfaces each command's outcome, including the ones the device rejected. */
    private fun logStep(step: ProfileStep) {
        val detail = step.output.lineSequence().firstOrNull { it.isNotBlank() }
        if (step.ok) {
            appendLog(if (detail == null) "${step.label} ok" else "${step.label}: $detail")
        } else {
            appendLog("${step.label} FALHOU: ${detail ?: "sem detalhe"}")
        }
    }

    /** Mirrors the active package into the shared state the overlay reads. */
    private fun syncBoostState(pkg: String?, label: String?) {
        BoostState.activePackage = pkg
        BoostState.activeLabel = label
    }

    private suspend fun revertProfile(pkg: String, previousZenMode: Int?) {
        val settings = store.current()
        ProfileEngine.revert(settings, pkg, previousZenMode).forEach(::logStep)
    }

    fun deactivate(silent: Boolean = false) {
        val s = _state.value
        val pkg = s.activePackage
        viewModelScope.launch {
            if (pkg != null && s.shizuku == ShizukuState.READY) {
                val previousZen = zenModeBefore
                zenModeBefore = null
                withContext(Dispatchers.IO) { revertProfile(pkg, previousZen) }
            }
            _state.update {
                it.copy(active = false, activePackage = null, metrics = DeviceMetrics())
            }
            syncBoostState(null, null)
            stopOverlay()
            if (!silent) appendLog("modo desativado, configurações restauradas")
        }
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            val target = _state.value.activePackage
            val sample = withContext(Dispatchers.IO) { MetricsReader.sample(target) }
            _state.update { it.copy(metrics = sample) }
        }
    }

    /** Frees background processes on demand, without activating the whole profile. */
    fun freeMemory() {
        if (_state.value.shizuku != ShizukuState.READY) {
            appendLog("Shizuku indisponível. Abra o app Shizuku e conceda a permissão.")
            return
        }
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { GameActions.killBackground() }
            appendLog("memória liberada${if (out.isBlank()) "" else ": $out"}")
        }
    }

    /** Undoes every change the profile made, leaving the device at its defaults. */
    fun restoreDefaults() {
        val s = _state.value
        val pkg = s.activePackage ?: s.selected?.packageName
        if (pkg == null) {
            appendLog("Selecione um jogo primeiro.")
            return
        }
        if (s.shizuku != ShizukuState.READY) {
            appendLog("Shizuku indisponível. Abra o app Shizuku e conceda a permissão.")
            return
        }
        viewModelScope.launch {
            val settings = store.current()
            val previousZen = zenModeBefore
            zenModeBefore = null
            withContext(Dispatchers.IO) { ProfileEngine.revert(settings, pkg, previousZen) }
            _state.update { it.copy(active = false, activePackage = null) }
            syncBoostState(null, null)
            stopOverlay()
            appendLog("padrões restaurados para $pkg")
        }
    }

    private fun appendLog(line: String) {
        _state.update { it.copy(log = (it.log + line).takeLast(60)) }
    }

    private companion object {
        /** FPS is resampled every 2s: each sample spawns a shell process. */
        const val FPS_REFRESH_MS = 2000L
    }
}

package com.turbokn.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbokn.app.core.DeviceMetrics
import com.turbokn.app.core.GameActions
import com.turbokn.app.core.GameWatcherService
import com.turbokn.app.core.MetricsReader
import com.turbokn.app.core.ProfileEngine
import com.turbokn.app.core.ShizukuNotifier
import com.turbokn.app.data.AppEntry
import com.turbokn.app.data.InstalledApps
import com.turbokn.app.data.ProfileSettings
import com.turbokn.app.data.SettingsStore
import com.turbokn.app.shizuku.ShizukuShell
import kotlinx.coroutines.Dispatchers
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
    val dnd: Boolean = false,
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

    init {
        restoreSettings()
        refreshShizuku()
        loadApps()
        viewModelScope.launch {
            while (true) {
                refreshShizuku()
                refreshAccessibility()
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
            _state.update {
                it.copy(
                    autoMode = saved.autoMode,
                    downscale = saved.downscale,
                    fps = saved.fps,
                    thermalStatus = saved.thermalStatus,
                    dnd = saved.dnd,
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
            startGame(target)
            refreshMetrics()
        }
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
        ProfileEngine.apply(settings, pkg).forEach(::appendLog)
    }

    private suspend fun revertProfile(pkg: String, previousZenMode: Int?) {
        val settings = store.current()
        ProfileEngine.revert(settings, pkg, previousZenMode)
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
            appendLog("padrões restaurados para $pkg")
        }
    }

    private fun appendLog(line: String) {
        _state.update { it.copy(log = (it.log + line).takeLast(60)) }
    }
}

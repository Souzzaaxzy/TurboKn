package com.turbokn.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turbokn.app.core.GameActions
import com.turbokn.app.core.GameWatcherService
import com.turbokn.app.core.ProfileEngine
import com.turbokn.app.core.ShizukuNotifier
import com.turbokn.app.data.AppEntry
import com.turbokn.app.data.InstalledApps
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

enum class ShizukuState { NOT_RUNNING, PERMISSION_DENIED, READY }

data class UiState(
    val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
    val apps: List<AppEntry> = emptyList(),
    val selected: AppEntry? = null,
    val downscale: String = "disable",
    val fps: Int = 0,
    val autoMode: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val active: Boolean = false,
    val activePackage: String? = null,
    val thermalStatus: Int = 0,
    val log: List<String> = emptyList(),
    val output: String = "",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val store = SettingsStore(app)

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
    }

    /** Loads the persisted profile so the UI matches what automatic mode uses. */
    private fun restoreSettings() {
        viewModelScope.launch {
            val saved = store.current()
            val entry = saved.packageName?.let { AppEntry(it, saved.label ?: it) }
            _state.update {
                it.copy(
                    autoMode = saved.autoMode,
                    selected = entry ?: it.selected,
                    downscale = saved.downscale,
                    fps = saved.fps,
                    thermalStatus = saved.thermalStatus,
                )
            }
        }
    }

    fun refreshShizuku() {
        val newState = when {
            !ShizukuShell.isBinderAlive -> ShizukuState.NOT_RUNNING
            !ShizukuShell.isPermissionGranted -> ShizukuState.PERMISSION_DENIED
            else -> ShizukuState.READY
        }
        if (newState == ShizukuState.READY && _state.value.shizuku != ShizukuState.READY) {
            ShizukuShell.bind()
        }
        _state.update { it.copy(shizuku = newState) }
    }

    private fun refreshAccessibility() {
        val enabled = isAccessibilityEnabled(getApplication())
        _state.update { it.copy(accessibilityEnabled = enabled) }
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
            val apps = withContext(Dispatchers.IO) {
                InstalledApps.launchable(getApplication())
            }
            _state.update { it.copy(apps = apps) }
        }
    }

    fun requestShizukuPermission() {
        ShizukuShell.requestPermission()
        viewModelScope.launch {
            delay(500)
            refreshShizuku()
            if (_state.value.shizuku == ShizukuState.READY) ShizukuShell.bind()
        }
    }

    fun select(entry: AppEntry) {
        _state.update { it.copy(selected = entry) }
        viewModelScope.launch { store.setTarget(entry.packageName, entry.label) }
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
            val lines = withContext(Dispatchers.IO) {
                ProfileEngine.apply(settings, target.packageName)
            }
            lines.forEach(::appendLog)
            ShizukuNotifier.clear(getApplication())
            _state.update { it.copy(active = true, activePackage = target.packageName) }
            refreshThermal()
        }
    }

    fun deactivate(silent: Boolean = false) {
        val s = _state.value
        val pkg = s.activePackage
        viewModelScope.launch {
            if (pkg != null && s.shizuku == ShizukuState.READY) {
                val settings = store.current()
                withContext(Dispatchers.IO) { ProfileEngine.revert(settings, pkg) }
            }
            _state.update { it.copy(active = false, activePackage = null) }
            if (!silent) appendLog("modo desativado, configurações restauradas")
        }
    }

    fun refreshThermal() {
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { GameActions.thermalStatus() }
            _state.update { it.copy(output = out) }
        }
    }

    fun runCustomCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) { ShizukuShell.exec(command) }
            appendLog("$ $command")
            appendLog(out.ifBlank { "(sem saída)" })
        }
    }

    private fun appendLog(line: String) {
        _state.update { it.copy(log = (it.log + line).takeLast(60)) }
    }
}

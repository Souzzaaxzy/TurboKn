package com.turbokn.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "turbokn_settings")

data class ProfileSettings(
    val autoMode: Boolean = false,
    val packageName: String? = null,
    val label: String? = null,
    val downscale: String = "disable",
    val fps: Int = 0,
    val thermalStatus: Int = 0,
    val fixedPerf: Boolean = false,
    val dnd: Boolean = false,
    val overlay: Boolean = true,
    val recentPackages: List<String> = emptyList(),
)

/**
 * Persists the profile so the automatic mode keeps working when the UI process
 * is not in memory.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val AUTO = booleanPreferencesKey("auto_mode")
        val PACKAGE = stringPreferencesKey("package_name")
        val LABEL = stringPreferencesKey("label")
        val DOWNSCALE = stringPreferencesKey("downscale")
        val FPS = intPreferencesKey("fps")
        val THERMAL = intPreferencesKey("thermal_status")
        val FIXED_PERF = booleanPreferencesKey("fixed_perf")
        val DND = booleanPreferencesKey("dnd")
        val OVERLAY = booleanPreferencesKey("overlay")
        val RECENTS = stringPreferencesKey("recent_packages")
    }

    val flow: Flow<ProfileSettings> = context.dataStore.data.map { prefs ->
        ProfileSettings(
            autoMode = prefs[Keys.AUTO] ?: false,
            packageName = prefs[Keys.PACKAGE],
            label = prefs[Keys.LABEL],
            downscale = prefs[Keys.DOWNSCALE] ?: "disable",
            fps = prefs[Keys.FPS] ?: 0,
            thermalStatus = prefs[Keys.THERMAL] ?: 0,
            fixedPerf = prefs[Keys.FIXED_PERF] ?: false,
            dnd = prefs[Keys.DND] ?: false,
            overlay = prefs[Keys.OVERLAY] ?: true,
            recentPackages = prefs[Keys.RECENTS]
                ?.split(RECENT_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
        )
    }

    suspend fun current(): ProfileSettings = flow.first()

    suspend fun setAutoMode(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO] = enabled }

    suspend fun setTarget(packageName: String, label: String) = context.dataStore.edit {
        it[Keys.PACKAGE] = packageName
        it[Keys.LABEL] = label
    }

    suspend fun setDownscale(value: String) = context.dataStore.edit { it[Keys.DOWNSCALE] = value }

    suspend fun setFps(value: Int) = context.dataStore.edit { it[Keys.FPS] = value }

    suspend fun setThermalStatus(value: Int) = context.dataStore.edit { it[Keys.THERMAL] = value }

    suspend fun setDnd(enabled: Boolean) = context.dataStore.edit { it[Keys.DND] = enabled }

    suspend fun setFixedPerf(enabled: Boolean) =
        context.dataStore.edit { it[Keys.FIXED_PERF] = enabled }

    suspend fun setOverlay(enabled: Boolean) = context.dataStore.edit { it[Keys.OVERLAY] = enabled }

    /**
     * Moves [packageName] to the front of the recent list, keeping the most
     * recently launched games first and capping the list length.
     */
    suspend fun pushRecent(packageName: String) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.RECENTS]
            ?.split(RECENT_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val updated = (listOf(packageName) + current.filter { it != packageName })
            .take(MAX_RECENTS)
        prefs[Keys.RECENTS] = updated.joinToString(RECENT_SEPARATOR)
    }

    companion object {
        private const val RECENT_SEPARATOR = "\n"
        private const val MAX_RECENTS = 8
    }
}

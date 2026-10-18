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
    }

    val flow: Flow<ProfileSettings> = context.dataStore.data.map { prefs ->
        ProfileSettings(
            autoMode = prefs[Keys.AUTO] ?: false,
            packageName = prefs[Keys.PACKAGE],
            label = prefs[Keys.LABEL],
            downscale = prefs[Keys.DOWNSCALE] ?: "disable",
            fps = prefs[Keys.FPS] ?: 0,
            thermalStatus = prefs[Keys.THERMAL] ?: 0,
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
}

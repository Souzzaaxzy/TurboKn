package com.turbokn.app.core

import com.turbokn.app.data.ProfileSettings

/**
 * Applies and reverts a performance profile. Shared by the UI and by the
 * automatic detection path so both behave identically.
 */
object ProfileEngine {

    /** @return human readable lines describing what was executed. */
    fun apply(settings: ProfileSettings, pkg: String): List<String> {
        val log = mutableListOf<String>()

        val mode = GameActions.setPerformanceMode(pkg)
        log += if (mode.isBlank()) "modo performance aplicado" else mode

        if (settings.downscale != "disable" || settings.fps != 0) {
            val config = GameActions.setInterventions(pkg, settings.downscale, settings.fps)
            log += if (config.isBlank()) "intervenções aplicadas" else config
        }

        val kill = GameActions.killBackground()
        log += "background liberado: ${kill.ifBlank { "ok" }}"

        if (settings.thermalStatus > 0) {
            val thermal = GameActions.overrideThermal(settings.thermalStatus)
            log += "térmica: ${thermal.ifBlank { "override aplicado" }}"
        }

        return log
    }

    /** Restores the device defaults for the given package. */
    fun revert(settings: ProfileSettings, pkg: String) {
        GameActions.resetGame(pkg)
        if (settings.thermalStatus > 0) {
            GameActions.resetThermal()
        }
    }
}

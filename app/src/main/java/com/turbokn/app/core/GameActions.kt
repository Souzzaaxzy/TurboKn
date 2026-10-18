package com.turbokn.app.core

import com.turbokn.app.shizuku.ShizukuShell

/**
 * Every privileged action the app can perform, expressed as the exact shell
 * command it issues. Keeping them here makes the behaviour auditable.
 */
object GameActions {

    /** Valid values accepted by `cmd game set --downscale`. */
    val DOWNSCALE_STEPS = listOf(
        "disable", "0.9", "0.85", "0.8", "0.75", "0.7", "0.65", "0.6", "0.55",
        "0.5", "0.45", "0.4", "0.35", "0.3"
    )

    /** Valid values accepted by `cmd game set --fps`. 0 disables the override. */
    val FPS_STEPS = listOf(0, 30, 45, 60, 90, 120)

    fun setPerformanceMode(pkg: String): String =
        ShizukuShell.exec("cmd game mode performance $pkg")

    fun setStandardMode(pkg: String): String =
        ShizukuShell.exec("cmd game mode standard $pkg")

    fun setInterventions(pkg: String, downscale: String, fps: Int): String {
        val builder = StringBuilder("cmd game set --mode 2")
        if (downscale != "disable") {
            builder.append(" --downscale ").append(downscale)
        }
        if (fps != 0) {
            builder.append(" --fps ").append(fps)
        }
        builder.append(' ').append(pkg)
        return ShizukuShell.exec(builder.toString())
    }

    fun resetGame(pkg: String): String =
        ShizukuShell.exec("cmd game reset $pkg")

    fun listModes(pkg: String): String =
        ShizukuShell.exec("cmd game list-modes $pkg")

    /** Frees background processes. Reduces jank; does not raise average FPS. */
    fun killBackground(): String =
        ShizukuShell.exec("am kill-all")

    fun forceStop(pkg: String): String =
        ShizukuShell.exec("am force-stop $pkg")

    /**
     * Overrides the reported thermal status so the platform stops throttling.
     * Values: 0 none, 1 light, 2 moderate, 3 severe, 4 critical, 5 emergency, 6 shutdown.
     */
    fun overrideThermal(status: Int): String =
        ShizukuShell.exec("cmd thermalservice override-status $status")

    fun resetThermal(): String =
        ShizukuShell.exec("cmd thermalservice reset")

    fun thermalStatus(): String =
        ShizukuShell.exec("dumpsys thermalservice | grep -i \"Thermal Status\"")

    fun foregroundPackage(): String =
        ShizukuShell.exec("dumpsys activity activities | grep -m1 mResumedActivity")

    /**
     * A package counts as a game when the platform reports game modes for it.
     * Non-game packages make `list-modes` fail or answer "not a game".
     */
    fun isGamePackage(pkg: String): Boolean {
        val output = ShizukuShell.exec("cmd game list-modes $pkg")
        if (output.startsWith("error:")) return false
        val normalized = output.lowercase()
        if (normalized.contains("not a game") || normalized.contains("unknown package")) return false
        return normalized.contains("mode")
    }
}

package com.turbokn.app.core

import com.turbokn.app.shizuku.ShizukuShell

/** A point-in-time reading shown on the dashboard. */
data class DeviceMetrics(
    val temperatureCelsius: Float? = null,
    val cpuClockMhz: Int? = null,
    val thermalStatus: Int? = null,
    val fps: Int? = null,
)

/**
 * Reads live device state through Shizuku. Every value is best effort: kernel
 * paths differ between vendors, so a missing reading stays null instead of
 * failing the whole sample.
 */
object MetricsReader {

    fun sample(targetPackage: String?): DeviceMetrics = DeviceMetrics(
        temperatureCelsius = readTemperature(),
        cpuClockMhz = readCpuClock(),
        thermalStatus = readThermalStatus(),
        fps = targetPackage?.let { readFps(it) },
    )

    /**
     * Reads every thermal zone in one shell call and keeps the highest reading.
     * Vendors differ on units and on which zone tracks the SoC, so implausible
     * values are discarded rather than shown as a wrong number.
     */
    private fun readTemperature(): Float? {
        // Each zone is echoed on its own line: /sys files may lack a trailing
        // newline, which would merge two readings into one bogus number.
        val output = ShizukuShell.exec(
            "for f in /sys/class/thermal/thermal_zone*/temp; do cat \"${'$'}f\"; echo; done"
        )
        if (output.startsWith("error:")) return null
        return output.lineSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .mapNotNull { raw ->
                when {
                    raw in 100..1500 -> raw / 10f
                    raw in 10..99 -> raw.toFloat()
                    else -> null
                }
            }
            .maxOrNull()
    }

    /** Highest current core clock, in MHz. Cores differ, so the max is shown. */
    private fun readCpuClock(): Int? {
        val output = ShizukuShell.exec(
            "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq; do cat \"${'$'}f\"; echo; done"
        )
        if (output.startsWith("error:")) return null
        return output.lineSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .maxOrNull()
            ?.let { it / 1000 }
    }

    private fun readThermalStatus(): Int? {
        val output = ShizukuShell.exec("dumpsys thermalservice")
        val match = Regex("Thermal Status: (\\d+)").find(output) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Percentage of rendered frames that were not janky, derived from the
     * frame stats the platform keeps per package. This is a smoothness
     * indicator, not a raw FPS counter.
     */
    private fun readFps(pkg: String): Int? {
        val output = ShizukuShell.exec("dumpsys gfxinfo $pkg framestats")
        if (output.startsWith("error:")) return null

        val total = Regex("Total frames rendered: (\\d+)").find(output)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val janky = Regex("Janky frames: (\\d+)").find(output)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (total <= 0) return null
        return ((total - janky) * 100 / total).coerceIn(0, 100)
    }
}

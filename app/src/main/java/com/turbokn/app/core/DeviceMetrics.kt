package com.turbokn.app.core

import com.turbokn.app.shizuku.ShizukuShell

/** A point-in-time reading shown on the dashboard. */
data class DeviceMetrics(
    val temperatureCelsius: Float? = null,
    val cpuClockMhz: Int? = null,
    val thermalStatus: Int? = null,
    /** Percentage of frames that were not janky. Not a frame rate. */
    val smoothness: Int? = null,
)

/**
 * Reads live device state through Shizuku.
 *
 * All four readings come from a single shell invocation. Each exec spawns a
 * process, and sampling every few seconds during a game would otherwise cost
 * frames in the very thing being measured. Every value is best effort: kernel
 * paths differ between vendors, so a missing reading stays null.
 */
object MetricsReader {

    fun sample(targetPackage: String?): DeviceMetrics {
        val script = buildString {
            append("echo __TEMP__; ")
            append("for f in /sys/class/thermal/thermal_zone*/temp; do cat \"${'$'}f\" 2>/dev/null; echo; done; ")
            append("echo __CLOCK__; ")
            append("for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq; do cat \"${'$'}f\" 2>/dev/null; echo; done; ")
            append("echo __THERMAL__; ")
            append("dumpsys thermalservice | grep -m1 \"Thermal Status:\"; ")
            if (targetPackage != null) {
                append("echo __FRAMES__; ")
                append("dumpsys gfxinfo $targetPackage | grep -E \"Total frames rendered|Janky frames\"")
            }
        }

        val output = ShizukuShell.exec(script)
        if (output.startsWith("error:")) return DeviceMetrics()

        val sections = splitSections(output)
        return DeviceMetrics(
            temperatureCelsius = parseTemperature(sections["__TEMP__"].orEmpty()),
            cpuClockMhz = parseClock(sections["__CLOCK__"].orEmpty()),
            thermalStatus = parseThermal(sections["__THERMAL__"].orEmpty()),
            smoothness = parseSmoothness(sections["__FRAMES__"].orEmpty()),
        )
    }

    private fun splitSections(output: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        var key: String? = null
        val buffer = StringBuilder()

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("__") && trimmed.endsWith("__")) {
                key?.let { sections[it] = buffer.toString() }
                buffer.setLength(0)
                key = trimmed
            } else if (key != null) {
                buffer.appendLine(trimmed)
            }
        }
        key?.let { sections[it] = buffer.toString() }
        return sections
    }

    /**
     * Keeps the highest plausible reading. Vendors differ on units and on which
     * zone tracks the SoC, so implausible values are discarded instead of being
     * shown as a wrong number.
     */
    private fun parseTemperature(raw: String): Float? = raw.lineSequence()
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { value ->
            when {
                value in 100..1500 -> value / 10f
                value in 10..99 -> value.toFloat()
                else -> null
            }
        }
        .maxOrNull()

    private fun parseClock(raw: String): Int? = raw.lineSequence()
        .mapNotNull { it.trim().toIntOrNull() }
        .maxOrNull()
        ?.let { it / 1000 }

    private fun parseThermal(raw: String): Int? =
        Regex("Thermal Status: (\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseSmoothness(raw: String): Int? {
        val total = Regex("Total frames rendered: (\\d+)").find(raw)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val janky = Regex("Janky frames: (\\d+)").find(raw)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (total <= 0) return null
        return ((total - janky) * 100 / total).coerceIn(0, 100)
    }
}

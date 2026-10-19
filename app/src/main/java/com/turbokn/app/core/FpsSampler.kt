package com.turbokn.app.core

import com.turbokn.app.shizuku.ShizukuShell

/**
 * Reads the real frame rate of a game.
 *
 * `dumpsys gfxinfo` only counts frames drawn by the UI toolkit. Games render
 * through a SurfaceView with OpenGL ES or Vulkan, so that counter stays at zero
 * for them — which is exactly why a naive implementation reports 0 FPS.
 *
 * Two sources are used, in order:
 *
 * 1. `dumpsys SurfaceFlinger --timestats`, which reports average FPS per
 *    composited layer. Google documents this as the supported way to measure
 *    game FPS. Clearing before dumping makes the number describe the current
 *    window instead of the whole session.
 * 2. `dumpsys SurfaceFlinger --latency <layer>`, the older per-layer frame
 *    timestamp buffer. Used when the device does not support timestats.
 *
 * The commands run in a single shell invocation each. Every exec spawns a
 * process, and doing several per sample would cost frames in the very game
 * being measured.
 */
object FpsSampler {

    private const val LAYER_PREFIX = "layerName = "
    private const val FPS_PREFIX = "averageFPS = "

    @Volatile
    private var fps: Int? = null

    @Volatile
    private var lastPackage: String? = null

    @Volatile
    private var timestatsAvailable = true

    /** Cached SurfaceFlinger layer name for the current package. */
    @Volatile
    private var layerName: String? = null

    fun sample(pkg: String): Int? {
        if (lastPackage != pkg) {
            lastPackage = pkg
            fps = null
            layerName = null
            timestatsAvailable = true
        }

        val measured = if (timestatsAvailable) {
            sampleViaTimestats(pkg) ?: sampleViaLatency(pkg)
        } else {
            sampleViaLatency(pkg)
        }

        fps = measured
        return measured
    }

    /** Enables the SurfaceFlinger statistics collection for this session. */
    fun start(pkg: String) {
        lastPackage = pkg
        fps = null
        layerName = null
        timestatsAvailable = true
        ShizukuShell.exec("dumpsys SurfaceFlinger --timestats -clear -enable")
    }

    fun reset(pkg: String? = null) {
        lastPackage = pkg
        fps = null
        layerName = null
        ShizukuShell.exec("dumpsys SurfaceFlinger --timestats -disable")
    }

    /**
     * Primary path. Returns null when the device has no timestats support, so
     * the caller can fall back.
     */
    private fun sampleViaTimestats(pkg: String): Int? {
        val output = ShizukuShell.exec(
            "dumpsys SurfaceFlinger --timestats -clear; " +
                "dumpsys SurfaceFlinger --timestats -dump"
        )
        if (output.startsWith("error:")) return null

        val value = parseLayerFps(output, pkg)
        if (value == null && !output.contains(LAYER_PREFIX)) {
            // No layer block at all means the flag is unsupported here.
            timestatsAvailable = false
        }
        return value
    }

    /**
     * Fallback path. The latency buffer holds up to 128 frames of timestamps;
     * the rate is the frame count over the span they cover, which avoids
     * depending on when we happened to call.
     */
    private fun sampleViaLatency(pkg: String): Int? {
        val layer = layerName ?: resolveLayer(pkg) ?: return null
        layerName = layer

        val output = ShizukuShell.exec("dumpsys SurfaceFlinger --latency \"$layer\"")
        if (output.startsWith("error:")) return null

        val timestamps = mutableListOf<Long>()
        for (line in output.lineSequence()) {
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size != 3) continue
            // Second column is the present time; zero means an empty slot.
            val present = fields[1].toLongOrNull() ?: continue
            if (present > 0L) timestamps += present
        }
        if (timestamps.size < 2) return null

        val span = timestamps.last() - timestamps.first()
        if (span <= 0L) return null
        val value = ((timestamps.size - 1) * 1_000_000_000L / span).toInt()
        return if (value in 1..240) value else null
    }

    /** Finds the SurfaceFlinger layer that belongs to [pkg]. */
    private fun resolveLayer(pkg: String): String? {
        val output = ShizukuShell.exec("dumpsys SurfaceFlinger --list")
        if (output.startsWith("error:")) return null
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.contains(pkg) }
            // Prefer the main game surface when several layers match.
            .maxByOrNull { line -> if (line.startsWith("SurfaceView")) 2 else 1 }
    }

    /**
     * Picks the FPS of the busiest layer belonging to [pkg]. A game usually has
     * several layers (main surface, overlays, video), and the main one presents
     * the most frames, so the highest reading is the right pick.
     */
    private fun parseLayerFps(output: String, pkg: String): Int? {
        var currentLayer: String? = null
        val candidates = mutableListOf<Int>()

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith(LAYER_PREFIX)) {
                currentLayer = line.removePrefix(LAYER_PREFIX)
                continue
            }
            if (!line.startsWith(FPS_PREFIX)) continue

            val layer = currentLayer ?: continue
            if (!layer.contains(pkg)) continue

            val value = line.removePrefix(FPS_PREFIX).trim().toFloatOrNull() ?: continue
            if (value > 0f) candidates += value.toInt()
        }

        return candidates.maxOrNull()?.coerceIn(0, 240)
    }
}

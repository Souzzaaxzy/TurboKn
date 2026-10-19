package com.turbokn.app.core

import com.turbokn.app.shizuku.ShizukuShell

/**
 * Measures the frame rate of a target package by sampling the platform's own
 * frame counter (`dumpsys gfxinfo`) and dividing the delta by the elapsed
 * time. Measuring our own frames would be meaningless: the overlay window is
 * drawn by a different process than the game.
 *
 * The first sample only establishes a baseline, so [current] stays null until
 * a second reading is available.
 */
object FpsSampler {

    private var lastPackage: String? = null
    private var lastFrames: Int? = null
    private var lastSampleNanos: Long = 0L

    @Volatile
    private var fps: Int? = null

    /** Latest computed rate, or null when it cannot be measured yet. */
    fun current(): Int? = fps

    fun sample(pkg: String): Int? {
        val total = readTotalFrames(pkg)
        if (total == null) {
            reset(pkg)
            return null
        }

        val now = System.nanoTime()
        val previousFrames = lastFrames
        val previousNanos = lastSampleNanos

        // A different target or a counter that went backwards (the game was
        // restarted) invalidates the baseline.
        if (lastPackage != pkg || previousFrames == null || total < previousFrames) {
            lastPackage = pkg
            lastFrames = total
            lastSampleNanos = now
            fps = null
            return null
        }

        val elapsedNanos = now - previousNanos
        lastFrames = total
        lastSampleNanos = now

        if (elapsedNanos < MIN_WINDOW_NANOS) return fps

        val delta = total - previousFrames
        val computed = (delta * 1_000_000_000L / elapsedNanos).toInt()
        // Ignore implausible values so a stalled counter never shows a spike.
        fps = if (computed in 0..MAX_FPS) computed else null
        return fps
    }

    fun reset(pkg: String? = null) {
        lastPackage = pkg
        lastFrames = null
        lastSampleNanos = 0L
        fps = null
    }

    /** Reads "Total frames rendered" from the platform's per-package stats. */
    private fun readTotalFrames(pkg: String): Int? {
        val output = ShizukuShell.exec("dumpsys gfxinfo $pkg")
        if (output.startsWith("error:")) return null
        if (output.contains("No process found") || output.contains("Unknown package")) return null
        return Regex("Total frames rendered: (\\d+)")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    private const val MAX_FPS = 240

    /** Below this, the delta is too small to be a meaningful rate. */
    private const val MIN_WINDOW_NANOS = 250_000_000L
}

package com.turbokn.app.core

import com.turbokn.app.data.ProfileSettings

/** Outcome of one command, so the UI can report what really happened. */
data class ProfileStep(
    val label: String,
    val output: String,
    val ok: Boolean,
)

/**
 * Applies and reverts a performance profile. Shared by the UI and by the
 * automatic detection path so both behave identically.
 *
 * Every step is reported individually: a profile that silently fails is worse
 * than one that says which command was rejected by the device.
 */
object ProfileEngine {

    /**
     * Applies the profile to [pkg].
     *
     * Note what is deliberately absent: `am kill-all`. Freeing background
     * processes the moment a game starts makes things worse, not better. It
     * kills services the game itself relies on and forces the system to reload
     * assets, producing exactly the stutter it is supposed to prevent. The
     * Android activity manager already deprioritises background processes when
     * a game takes the foreground. The manual button in the UI still offers it,
     * because freeing memory between sessions is a different situation.
     */
    fun apply(settings: ProfileSettings, pkg: String): List<ProfileStep> {
        val steps = mutableListOf<ProfileStep>()

        steps += run("modo performance", GameActions.setPerformanceMode(pkg))

        if (settings.downscale != "disable" || settings.fps != 0) {
            steps += run(
                "escala/FPS",
                GameActions.setInterventions(pkg, settings.downscale, settings.fps),
            )
        }

        if (settings.fixedPerf) {
            steps += run("clocks fixos", GameActions.setFixedPerformance(true))
        }

        if (settings.dnd) {
            steps += run("não perturbe", GameActions.setDnd(true))
        }

        if (settings.thermalStatus > 0) {
            steps += run("térmica", GameActions.overrideThermal(settings.thermalStatus))
        }

        return steps
    }

    /**
     * Restores the device defaults. [previousZenMode] is the value read before
     * DND was enabled, so the user's own setting wins over a hard-coded "off".
     */
    fun revert(
        settings: ProfileSettings,
        pkg: String,
        previousZenMode: Int? = null,
    ): List<ProfileStep> {
        val steps = mutableListOf<ProfileStep>()

        if (settings.fixedPerf) {
            steps += run("clocks fixos", GameActions.setFixedPerformance(false))
        }
        if (settings.thermalStatus > 0) {
            steps += run("térmica", GameActions.resetThermal())
        }
        if (settings.dnd) {
            steps += run("não perturbe", GameActions.restoreZenMode(previousZenMode ?: 0))
        }
        steps += run("modo do jogo", GameActions.resetGame(pkg))

        return steps
    }

    private fun run(label: String, output: String): ProfileStep {
        // The shell command reports success with an empty output; anything that
        // looks like a rejection is surfaced instead of being swallowed.
        val lowered = output.lowercase()
        val failed = lowered.startsWith("error:") ||
            lowered.contains("exception") ||
            lowered.contains("unknown package") ||
            lowered.contains("invalid")
        return ProfileStep(label, output, ok = !failed)
    }
}

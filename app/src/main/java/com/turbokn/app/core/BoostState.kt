package com.turbokn.app.core

/**
 * Process-wide flag telling the overlay which package TurboKn is boosting.
 *
 * The accessibility service and the UI share the app process, so a plain
 * singleton is enough here; nothing crosses a process boundary.
 */
object BoostState {

    /** Package currently being boosted, or null when no profile is active. */
    @Volatile
    var activePackage: String? = null

    /** Label shown in the in-game banner. */
    @Volatile
    var activeLabel: String? = null

    /** Whether the user wants the in-game overlay at all. */
    @Volatile
    var overlayEnabled: Boolean = false
}

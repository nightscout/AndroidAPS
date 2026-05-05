package app.aaps.core.data.model

/**
 * Runtime state of the currently active scene.
 * Stores the scene being run and a snapshot of prior state for revert on deactivation.
 */
data class ActiveSceneState(
    /** The scene that is currently active */
    val scene: Scene,
    /** Timestamp when the scene was activated (epoch millis) */
    val activatedAt: Long,
    /** Duration in milliseconds (0 = indefinite, manual end only) */
    val durationMs: Long,
    /** Snapshot of prior state for revert on deactivation */
    val priorState: PriorState
) {

    /** Calculated end time (null if indefinite) */
    val endsAt: Long?
        get() = if (durationMs > 0) activatedAt + durationMs else null

    /** Whether the scene has expired based on current time */
    fun isExpired(now: Long): Boolean = endsAt?.let { now >= it } ?: false

    /** Remaining time in milliseconds (null if indefinite, 0 if expired) */
    fun remainingMs(now: Long): Long? = endsAt?.let { maxOf(0L, it - now) }

    /**
     * Snapshot of system state before scene activation, used for revert.
     * TT/PS/RM revert works by shortening the scene's own record so the resolver
     * naturally falls back to the underlying state — no prior values needed.
     * SMB has no duration model, so its prior value is captured explicitly.
     */
    data class PriorState(
        /** SMB enabled state before activation (null if SMB wasn't changed by scene) */
        val smbEnabled: Boolean? = null,
        // Record IDs created by scene activation — used to detect manual overrides at revert time
        /** ID of the TT record created by scene (null if scene didn't set TT) */
        val sceneTtId: Long? = null,
        /** ID of the ProfileSwitch record created by scene (null if scene didn't change profile) */
        val scenePsId: Long? = null,
        /** ID of the RunningMode record created by scene (null if scene didn't change loop mode) */
        val sceneRunningModeId: Long? = null,
        /** ID of the TherapyEvent record created by scene (null if scene didn't log a CarePortalEvent) */
        val sceneTherapyEventId: Long? = null
    )
}

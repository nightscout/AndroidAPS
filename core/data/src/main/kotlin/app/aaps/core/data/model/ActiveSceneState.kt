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
     */
    data class PriorState(
        /** SMB enabled state before activation (null if SMB wasn't changed by scene) */
        val smbEnabled: Boolean? = null,
        /** Active profile name before activation (null if profile wasn't changed by scene) */
        val profileName: String? = null,
        /** Profile percentage before activation (null if profile wasn't changed by scene) */
        val profilePercentage: Int? = null,
        /** Profile time shift before activation (null if profile wasn't changed by scene) */
        val profileTimeShiftHours: Int? = null,
        /** Running mode before activation (null if loop mode wasn't changed by scene) */
        val runningMode: RM.Mode? = null,
        // Record IDs created by scene activation — used to detect manual overrides at revert time
        /** ID of the TT record created by scene (null if scene didn't set TT) */
        val sceneTtId: Long? = null,
        /** ID of the ProfileSwitch record created by scene (null if scene didn't change profile) */
        val scenePsId: Long? = null,
        /** ID of the RunningMode record created by scene (null if scene didn't change loop mode) */
        val sceneRunningModeId: Long? = null
    )
}

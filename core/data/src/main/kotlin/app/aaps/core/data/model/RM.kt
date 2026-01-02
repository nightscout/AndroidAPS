package app.aaps.core.data.model

import java.util.TimeZone

data class RM(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    /** Current running mode. */
    var mode: Mode,
    /** Planned duration in milliseconds */
    var duration: Long,
    /**
     * true if forced automatically in loop plugin,
     * false if initiated by user
     */
    var autoForced: Boolean = false,
    /** List of reasons for automated mode change */
    var reasons: String? = null
) : HasIDs {

    fun contentEqualsTo(other: RM): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            mode == other.mode &&
            duration == other.duration &&
            autoForced == other.autoForced &&
            isValid == other.isValid

    fun onlyNsIdAdded(previous: RM): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    fun isTemporary() = duration > 0

    enum class Mode {
        OPEN_LOOP,
        CLOSED_LOOP,
        CLOSED_LOOP_LGS,

        // Temporary only
        DISABLED_LOOP,
        SUPER_BOLUS,
        DISCONNECTED_PUMP,
        SUSPENDED_BY_PUMP,
        SUSPENDED_BY_USER,
        SUSPENDED_BY_DST,

        /**
         * Not a real mode but only option to cancel temporary mode
         * (ie. RECONNECT_PUMP, RESUME_LOOP, CANCEL_SUPERBOLUS)
         */
        RESUME
        ;

        fun isClosedLoopOrLgs() = this == CLOSED_LOOP || this == CLOSED_LOOP_LGS
        fun isLoopRunning() = this == OPEN_LOOP || this == CLOSED_LOOP || this == CLOSED_LOOP_LGS
        fun isSuspended() = this == DISCONNECTED_PUMP || this == SUSPENDED_BY_PUMP || this == SUSPENDED_BY_USER || this == SUSPENDED_BY_DST || this == SUPER_BOLUS
        // DISABLED_LOOP is added to "mustBeTemporary" to be properly rendered in NS
        fun mustBeTemporary() = this == DISCONNECTED_PUMP || this == SUSPENDED_BY_PUMP || this == SUSPENDED_BY_USER || this == SUSPENDED_BY_DST || this == SUPER_BOLUS || this == DISABLED_LOOP

        companion object {

            fun fromString(reason: String?) = Mode.entries.firstOrNull { it.name == reason } ?: DEFAULT_MODE
        }
    }

    companion object {
        val DEFAULT_MODE = Mode.DISABLED_LOOP
    }
}
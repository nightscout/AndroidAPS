package app.aaps.core.data.model

import java.util.TimeZone

data class RM(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    override var timestamp: Long,
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
) : HasIDs, TimeStamped {

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

        // Can be permanent (duration=0) or temporary; NS sync layer substitutes a long
        // wire duration so the offline marker still renders.
        DISABLED_LOOP,

        // Temporary only
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

        /**
         * True when the loop algorithm should not run / not dispatch new dosing decisions.
         * Includes SUPER_BOLUS (the bolus is delivered by the wizard, basal is forced to 0)
         * and all explicit suspends. **Does not** mean "manual bolus blocked" — for that
         * use [PumpCommandGate.check] with [PumpCommandGate.CommandKind.BOLUS].
         */
        fun pausesLoopExecution() = this == DISCONNECTED_PUMP || this == SUSPENDED_BY_PUMP || this == SUSPENDED_BY_USER || this == SUSPENDED_BY_DST || this == SUPER_BOLUS
        fun mustBeTemporary() = this == DISCONNECTED_PUMP || this == SUSPENDED_BY_PUMP || this == SUSPENDED_BY_USER || this == SUSPENDED_BY_DST || this == SUPER_BOLUS

        companion object {

            fun fromString(reason: String?) = Mode.entries.firstOrNull { it.name == reason } ?: DEFAULT_MODE
        }
    }

    companion object {

        val DEFAULT_MODE = Mode.DISABLED_LOOP
    }
}
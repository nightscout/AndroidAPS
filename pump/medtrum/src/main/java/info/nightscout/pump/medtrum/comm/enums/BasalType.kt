package info.nightscout.pump.medtrum.comm.enums

enum class BasalType {
    NONE,
    STANDARD,
    EXERCISE,
    HOLIDAY,
    PROGRAM_A,
    PROGRAM_B,
    ABSOLUTE_TEMP,
    RELATIVE_TEMP,
    PROGRAM_C,
    PROGRAM_D,
    SICK,
    AUTO,
    NEW,
    SUSPEND_LOW_GLUCOSE,
    SUSPEND_PREDICT_LOW_GLUCOSE,
    SUSPEND_AUTO,
    SUSPEND_MORE_THAN_MAX_PER_HOUR,
    SUSPEND_MORE_THAN_MAX_PER_DAY,
    SUSPEND_MANUAL,
    SUSPEND_KEY_LOST,
    STOP_OCCLUSION,
    STOP_EXPIRED,
    STOP_EMPTY,
    STOP_PATCH_FAULT,
    STOP_PATCH_FAULT2,
    STOP_BASE_FAULT,
    STOP_DISCARD,
    STOP_BATTERY_EXHAUSTED,
    STOP,
    PAUSE_INTERRUPT,
    PRIME,
    AUTO_MODE_START,
    AUTO_MODE_EXIT,
    AUTO_MODE_TARGET_100,
    AUTO_MODE_TARGET_110,
    AUTO_MODE_TARGET_120,
    AUTO_MODE_BREAKFAST,
    AUTO_MODE_LUNCH,
    AUTO_MODE_DINNER,
    AUTO_MODE_SNACK,
    AUTO_MODE_EXERCISE_START,
    AUTO_MODE_EXERCISE_EXIT;

    fun isTempBasal(): Boolean {
        return this == ABSOLUTE_TEMP || this == RELATIVE_TEMP
    }

    fun isSuspendedByPump(): Boolean {
        return this in SUSPEND_LOW_GLUCOSE..STOP
    }

    companion object {

        fun fromBasalEndReason(endReason: BasalEndReason): BasalType {
            return when (endReason) {
                BasalEndReason.SUSPEND_LOW_GLUCOSE            -> SUSPEND_LOW_GLUCOSE
                BasalEndReason.SUSPEND_PREDICT_LOW_GLUCOSE    -> SUSPEND_PREDICT_LOW_GLUCOSE
                BasalEndReason.SUSPEND_AUTO                   -> SUSPEND_AUTO
                BasalEndReason.SUSPEND_MORE_THAN_MAX_PER_HOUR -> SUSPEND_MORE_THAN_MAX_PER_HOUR
                BasalEndReason.SUSPEND_MORE_THAN_MAX_PER_DAY  -> SUSPEND_MORE_THAN_MAX_PER_DAY
                BasalEndReason.SUSPEND_MANUAL                 -> SUSPEND_MANUAL
                else                                          -> NONE
            }
        }
    }
}

package app.aaps.pump.eopatch.code

enum class PatchStep {
    SAFE_DEACTIVATION,
    MANUALLY_TURNING_OFF_ALARM,
    DISCARDED,
    DISCARDED_FOR_CHANGE,
    DISCARDED_FROM_ALARM,
    WAKE_UP,
    CONNECT_NEW,
    REMOVE_NEEDLE_CAP,
    REMOVE_PROTECTION_TAPE,
    SAFETY_CHECK,
    ROTATE_KNOB,
    ROTATE_KNOB_NEEDLE_INSERTION_ERROR,
    BASAL_SCHEDULE,
    SETTING_REMINDER_TIME,
    CHECK_CONNECTION,
    CANCEL,
    COMPLETE,
    BACK_TO_HOME,
    FINISH;

    val isSafeDeactivation: Boolean
        get() = this == SAFE_DEACTIVATION

    val isCheckConnection: Boolean
        get() = this == CHECK_CONNECTION
}
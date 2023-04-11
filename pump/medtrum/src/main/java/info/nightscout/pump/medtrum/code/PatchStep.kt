package info.nightscout.pump.medtrum.code

enum class PatchStep {
    SAFE_DEACTIVATION,
    MANUALLY_TURNING_OFF_ALARM,
    DISCARDED,
    DISCARDED_FOR_CHANGE,
    DISCARDED_FROM_ALARM,
    PREPARE_PATCH,
    PRIME,
    ATTACH_PATCH,
    ACTIVATE,
    CANCEL,
    COMPLETE,
    BACK_TO_HOME,
    FINISH;
}

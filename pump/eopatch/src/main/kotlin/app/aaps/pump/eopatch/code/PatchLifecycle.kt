package app.aaps.pump.eopatch.code

enum class PatchLifecycle(val rawValue: Int) {
    SHUTDOWN(1),
    BONDED(2),
    SAFETY_CHECK(3),
    REMOVE_NEEDLE_CAP(4),
    REMOVE_PROTECTION_TAPE(5),
    ROTATE_KNOB(6),
    BASAL_SETTING(7),
    ACTIVATED(8);

    val isShutdown: Boolean
        get() = this == SHUTDOWN

    val isActivated: Boolean
        get() = this == ACTIVATED

}

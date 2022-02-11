package info.nightscout.androidaps.plugins.pump.eopatch.code


enum class DeactivationStatus constructor(val rawValue: Int) {
    DEACTIVATION_FAILED(0),
    NORMAL_DEACTIVATED(1),
    FORCE_DEACTIVATED(2);

    val isDeactivated: Boolean
        get() = this == NORMAL_DEACTIVATED || this == FORCE_DEACTIVATED

    val isNormalSuccess: Boolean
        get() = this == NORMAL_DEACTIVATED

    val isNormalFailed: Boolean
        get() = this == DEACTIVATION_FAILED || this == FORCE_DEACTIVATED

    companion object {
        @JvmStatic
        fun of(isSuccess: Boolean, forced: Boolean): DeactivationStatus {
            return when {
                isSuccess -> NORMAL_DEACTIVATED
                forced -> FORCE_DEACTIVATED
                else -> DEACTIVATION_FAILED
            }
        }
    }
}

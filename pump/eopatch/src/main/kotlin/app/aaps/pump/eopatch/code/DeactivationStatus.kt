package app.aaps.pump.eopatch.code

enum class DeactivationStatus {
    DEACTIVATION_FAILED,
    NORMAL_DEACTIVATED,
    FORCE_DEACTIVATED;

    val isDeactivated: Boolean
        get() = this == NORMAL_DEACTIVATED || this == FORCE_DEACTIVATED

    companion object {

        @JvmStatic
        fun of(isSuccess: Boolean, forced: Boolean): DeactivationStatus {
            return when {
                isSuccess -> NORMAL_DEACTIVATED
                forced    -> FORCE_DEACTIVATED
                else      -> DEACTIVATION_FAILED
            }
        }
    }
}

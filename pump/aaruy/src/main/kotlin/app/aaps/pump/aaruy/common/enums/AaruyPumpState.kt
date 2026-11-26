package app.aaps.pump.aaruy.common.enums

enum class AaruyPumpState(val state: Int) {
    NO_RUNNING(0),
    BASALRATE_RUNNING(1),
    TMPBASALRATE_RUNNING(2),
    LARGEDOSE_RUNNING(3),
    EXHAUSTING(4),
    PUSHBACK_RUNNING(5),
    PUSHFORWARD_RUNNING(6),
    PAUSE_RUNNING(7),
    STOP_RUNNING(8),
    FIRST_INIT(125);

    fun isSuspendedByPump(): Boolean {
        return this in PAUSE_RUNNING..STOP_RUNNING
    }

    fun isNormalStatus(): Boolean {
        return this in NO_RUNNING..LARGEDOSE_RUNNING
    }

    fun isCanDoRefresh(): Boolean {
        return (this != FIRST_INIT && this != LARGEDOSE_RUNNING)
    }

    fun isCanBolus(): Boolean {
        return (this == BASALRATE_RUNNING || this == TMPBASALRATE_RUNNING)
    }

    fun isCanTmpBasal(): Boolean {
        return (this == BASALRATE_RUNNING)
    }

    companion object {

        fun fromInt(state: Int) = entries.find { it.state == state }
            ?: FIRST_INIT
    }
}

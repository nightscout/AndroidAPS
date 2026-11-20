package app.aaps.plugins.dexcomoneplus.session

/**
 * How often a sensor has refused our authentication in a row, kept per sensor slot.
 *
 * A G7 / ONE+ locks itself for a while after repeated failed authentications: four refusals in a
 * row lock the sensor (this is what nightscout/AndroidAPS PR 5397 and LoopKit G7SensorKit work
 * around). A locked sensor sends no readings to anyone, so the loop loses its glucose. Our session
 * loop retries on its own, and the plugin watchdog starts a new session when one ends, so without a
 * count that survives both, a wrong code or a slot held by another phone could be tried until the
 * sensor locks.
 *
 * Saved in the slot's private store, so an app restart does not start the count again.
 *
 * @property address the sensor the refusals came from. A count for another sensor is not this
 *   sensor's count.
 * @property count refusals in a row since the last successful authentication.
 * @property lastAtMs wall clock of the last refusal, 0 when none.
 * @property lastWithSavedKey the last refusal was of a saved shared key (short auth), which is
 *   fixed without the user by running the full pairing once.
 */
data class OnePlusAuthRefusals(
    val address: String? = null,
    val count: Int = 0,
    val lastAtMs: Long = 0L,
    val lastWithSavedKey: Boolean = false,
)

/** Where [OnePlusAuthRefusals] lives between sessions. The driver backs it with the slot store. */
interface OnePlusAuthRefusalLedger {

    fun load(): OnePlusAuthRefusals

    fun save(refusals: OnePlusAuthRefusals)
}

/** Keeps nothing. For tests and for sessions built without a store. */
class OnePlusAuthRefusalLedgerInMemory(private var value: OnePlusAuthRefusals = OnePlusAuthRefusals()) : OnePlusAuthRefusalLedger {

    override fun load(): OnePlusAuthRefusals = value

    override fun save(refusals: OnePlusAuthRefusals) {
        value = refusals
    }
}

/**
 * The rule: never let the sensor reach the refusal that locks it.
 *
 * - After a refusal we wait [pauseMs] before we ask that sensor again. The one exception is a
 *   refused saved key: the next try is the full pairing with the code, which is what fixes it, so
 *   it may go at once.
 * - After [MAX_REFUSALS_IN_A_ROW] refusals in a row we stop. That is one below the four that lock
 *   the sensor. Only the user can start again, from the start screen.
 * - A successful authentication clears the count.
 *
 * Only refusals count. A dropped link, a timeout or a missing advertisement is not a refusal and
 * keeps the normal retry policy.
 */
class OnePlusAuthRefusalPolicy(
    private val pauseMs: Long = REFUSAL_PAUSE_MS,
) {

    sealed class Decision {

        /** The sensor may be asked now. */
        data object Allowed : Decision()

        /** The sensor refused us recently. Do not ask before [untilMs] (wall clock). */
        data class Wait(val untilMs: Long) : Decision()

        /** The sensor refused us too often. Do not ask again until the user starts it again. */
        data object Locked : Decision()
    }

    fun decide(refusals: OnePlusAuthRefusals, address: String, nowMs: Long): Decision {
        if (!isSameSensor(refusals, address) || refusals.count <= 0) return Decision.Allowed
        if (refusals.count >= MAX_REFUSALS_IN_A_ROW) return Decision.Locked
        if (refusals.lastWithSavedKey) return Decision.Allowed
        val untilMs = refusals.lastAtMs + pauseMs
        return if (nowMs >= untilMs) Decision.Allowed else Decision.Wait(untilMs)
    }

    /** One more refusal from [address]. A refusal from another sensor starts its own count. */
    fun afterRefusal(refusals: OnePlusAuthRefusals, address: String, nowMs: Long, withSavedKey: Boolean): OnePlusAuthRefusals {
        val previous = if (isSameSensor(refusals, address)) refusals.count else 0
        return OnePlusAuthRefusals(
            address = address.uppercase(),
            count = previous + 1,
            lastAtMs = nowMs,
            lastWithSavedKey = withSavedKey,
        )
    }

    /** The sensor took our key: the count is over. */
    fun afterSuccess(): OnePlusAuthRefusals = OnePlusAuthRefusals()

    /**
     * The user pressed Connect on the start screen.
     *
     * Another sensor or another code is a fresh start: the refusals were about the old pair. The same
     * sensor with the same code gets one more try and no more, so the next refusal stops us again.
     * The pause still applies to that try: pressing Connect again and again must not turn into
     * refusals in a row, so even the user gets at most one try per pause.
     */
    fun afterUserStart(refusals: OnePlusAuthRefusals, address: String, codeChanged: Boolean): OnePlusAuthRefusals {
        if (codeChanged || !isSameSensor(refusals, address) || refusals.count <= 0) return OnePlusAuthRefusals()
        return refusals.copy(count = refusals.count.coerceAtMost(MAX_REFUSALS_IN_A_ROW - 1))
    }

    private fun isSameSensor(refusals: OnePlusAuthRefusals, address: String): Boolean =
        refusals.address != null && refusals.address.equals(address, ignoreCase = true)

    companion object {

        /** One below the four refusals in a row that lock a G7 / ONE+. */
        const val MAX_REFUSALS_IN_A_ROW = 3

        /** The pause after a refusal. The same 15 minutes PR 5397 uses. */
        const val REFUSAL_PAUSE_MS = 15L * 60L * 1000L

        /** Warm-up message while we wait after a refusal. Mapped to plain text by the screens. */
        const val MESSAGE_PAUSED = "ONEPLUS_REFUSED_PAUSE"

        /** Warm-up message once we stopped. Mapped to plain text by the screens. */
        const val MESSAGE_LOCKED = "ONEPLUS_REFUSED_STOP"
    }
}

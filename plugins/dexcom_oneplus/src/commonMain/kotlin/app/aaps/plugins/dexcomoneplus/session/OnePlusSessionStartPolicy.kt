package app.aaps.plugins.dexcomoneplus.session

/**
 * Pure policy for Control SessionStart after TransmitterTime (Dexcom-safe).
 *
 * Never auto SessionStop — stopping the transmitter session can prevent recovery
 * in the official Dexcom app.
 */
object OnePlusSessionStartPolicy {

    enum class Action {
        /** Transmitter already has a session — EGV/backfill only. */
        AttachOnly,

        /** No session yet — send SessionStartTx 0x26. */
        SessionStart,

        /** No session and start not requested — EGV only. */
        EgvOnly,
    }

    fun decide(requestNewSensorStart: Boolean, sessionAlreadyInProgress: Boolean): Action =
        when {
            sessionAlreadyInProgress -> Action.AttachOnly
            requestNewSensorStart -> Action.SessionStart
            else -> Action.EgvOnly
        }

    /** Reconnect attempts must never re-issue SessionStart. */
    fun wantSessionStartOnAttempt(requestNewSensorStart: Boolean, attempt: Int): Boolean =
        requestNewSensorStart && attempt == 0
}

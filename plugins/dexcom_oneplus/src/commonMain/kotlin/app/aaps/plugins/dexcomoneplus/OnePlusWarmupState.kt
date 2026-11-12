package app.aaps.plugins.dexcomoneplus

/**
 * Warm-up clock exposed to UI. Prefer protocol remainingMs when available.
 *
 * Helpers for remaining resolution live in `warmup.OnePlusWarmupClock` (pure, unit-tested).
 */
data class OnePlusWarmupState(
    val phase: Phase,
    val remainingMs: Long? = null,
    val endsAtEpochMs: Long? = null,
    val message: String? = null,
    /**
     * Whole minutes the session has been waiting for the sensor's advertisement while
     * [Phase.RECONNECTING] (null outside that wait). Typed rather than parsed back out of
     * [message], so the UI can tell "reconnecting, normal duty cycle" from "silent for 12 min".
     */
    val advSilenceMinutes: Long? = null,
    /**
     * Another ONE+ transmitter is advertising while the stored MAC never does — the sensor was
     * probably replaced, or started into the other slot. Surfaced so the user is told to re-run the
     * sensor start instead of waiting forever on a MAC that will never answer.
     */
    val staleMacSuspected: Boolean = false,
    /**
     * When the sensor says its session started: the time of the reading minus the session age that a
     * G7 / ONE+ glucose message carries. Null when the message does not say. AAPS takes it as the
     * insertion time, so a sensor that was already running keeps its real age.
     */
    val sensorStartEpochMs: Long? = null,
) {
    enum class Phase {
        IDLE,
        PAIRING,

        /** GATT connect / service discovery in progress (first attempt). */
        CONNECTING,

        /** Transient failure — retry loop is actively re-establishing (NOT terminal). */
        RECONNECTING,
        WARMING,
        READY,
        FAILED,
    }
}

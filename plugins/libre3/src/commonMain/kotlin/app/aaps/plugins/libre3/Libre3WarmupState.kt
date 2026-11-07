package app.aaps.plugins.libre3

/**
 * Warm-up and session phase shown by the UI.
 *
 * The sensor does not send a "remaining" value. Remaining time is computed from the warm-up
 * length read over NFC minus the sensor life counter, floored at zero. The helper lives in
 * `warmup.Libre3WarmupClock` so it can be unit tested on its own.
 */
data class Libre3WarmupState(
    val phase: Phase,
    val remainingMs: Long? = null,
    val endsAtEpochMs: Long? = null,
    val message: String? = null,
) {

    enum class Phase {

        /** Nothing running. */
        IDLE,

        /** NFC activation or the BLE handshake is running. */
        PAIRING,

        /** GATT connect and service discovery, first attempt. */
        CONNECTING,

        /** Link was lost and the retry loop is working. Not a final state. */
        RECONNECTING,

        /** Sensor is warming up. Glucose must not be sent to AAPS in this phase. */
        WARMING,

        /** Session is up and glucose is usable. */
        READY,

        /** Final failure. The user has to act. */
        FAILED
    }
}

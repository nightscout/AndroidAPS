package app.aaps.plugins.source.compose

/**
 * Warm-up phase of a CGM sensor, with no vendor name on it.
 *
 * The native driver maps its own phases onto these seven, so the screens work with this one enum.
 *
 * The generic [app.aaps.core.interfaces.source.CgmWarmupStatus] cannot carry this: by contract it
 * is null once the wait is over, so it has no IDLE, READY or FAILED, and these screens have to tell
 * those three apart - they pick the colour of the chip, they stop the countdown, and they clear the
 * local fallback timer.
 */
enum class CgmWarmupPhase {

    /** Nothing running. */
    IDLE,

    /** The BLE handshake is running. */
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
    FAILED,
}

/**
 * The warm-up facts the shared countdown rules read.
 *
 * A driver state stripped down to the three fields those rules use, so the rules can live in shared
 * code while the driver classes stay on Android.
 *
 * @param phase where the warm-up is right now.
 * @param remainingMs time left in ms if the protocol says so, else null.
 * @param endsAtEpochMs wall-clock end in epoch ms if known, else null.
 */
data class CgmWarmupInfo(
    val phase: CgmWarmupPhase,
    val remainingMs: Long? = null,
    val endsAtEpochMs: Long? = null,
)

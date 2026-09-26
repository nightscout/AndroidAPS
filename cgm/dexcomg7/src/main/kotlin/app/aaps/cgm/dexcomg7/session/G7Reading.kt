package app.aaps.cgm.dexcomg7.session

import app.aaps.cgm.dexcomg7.protocol.AlgorithmState
import app.aaps.cgm.dexcomg7.protocol.G7GlucoseLimits
import app.aaps.cgm.dexcomg7.protocol.G7Trend

/** One glucose reading from the sensor, live or from backfill, with its time on the phone's clock. */
data class G7Reading(
    val timestamp: Long,
    /** mg/dL as sent; null when the sensor sent no value (for example during warmup). */
    val glucose: Int?,
    val trend: G7Trend?,
    val trendRate: Double?,
    val algorithmState: AlgorithmState,
    /** A value that came from a calibration, shown by the sensor but not measured. */
    val displayOnly: Boolean,
    val fromBackfill: Boolean
) {

    /**
     * True when this reading may be given to the loop: a value, a sensor in the OK state, and not a
     * calibration echo. The loop should get nothing rather than a number the sensor does not stand behind.
     */
    val isUsable: Boolean get() = glucose != null && algorithmState.hasReliableGlucose && !displayOnly

    /** The value limited to what the sensor can measure (40..400 mg/dL). */
    val clampedGlucose: Int? get() = glucose?.coerceIn(G7GlucoseLimits.MINIMUM, G7GlucoseLimits.MAXIMUM)
}

/** How the link to the sensor is doing, for the status screen. */
enum class G7ConnectionStatus {

    /** The source is not running. */
    STOPPED,

    /** Nothing paired. */
    UNPAIRED,

    /** Bluetooth is off or not allowed. */
    BLOCKED,

    /** Waiting for the sensor's next advertisement. The normal state between readings. */
    WAITING,
    CONNECTED,

    /** A pairing run owns Bluetooth for now. */
    PAIRING
}

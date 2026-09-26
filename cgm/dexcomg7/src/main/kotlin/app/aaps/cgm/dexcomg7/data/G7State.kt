package app.aaps.cgm.dexcomg7.data

import app.aaps.cgm.dexcomg7.protocol.AlgorithmState
import app.aaps.cgm.dexcomg7.protocol.G7Lifecycle
import app.aaps.cgm.dexcomg7.protocol.G7LifecycleState
import app.aaps.cgm.dexcomg7.protocol.G7SensorModel
import kotlinx.serialization.Serializable

/**
 * Everything this driver remembers about the paired sensor. Saved as one JSON value, never exported:
 * it holds the pairing code and the key.
 *
 * Times are milliseconds since the epoch.
 */
@Serializable
data class G7State(
    /** Bluetooth address of the paired sensor. Null until a pairing has succeeded. */
    val address: String? = null,
    /** 4-digit code from the applicator. Kept so a lost key can be made again without the user. */
    val pairingCode: String? = null,
    /** 16-byte key agreed at pairing, as hex. Lets a reconnect skip the key exchange. */
    val sharedKeyHex: String? = null,
    /** Name reported by the sensor ("DXCMxx" / "Dexcomxx"). Set by the first reading. */
    val sensorName: String? = null,
    /** Serial from the applicator barcode, or from the sensor once it has told us. */
    val serial: String? = null,
    val firmware: String? = null,
    val activatedAt: Long? = null,
    /**
     * Phone time of the sensor's second zero, used to turn sensor seconds into phone time. Starts equal
     * to [activatedAt] but, unlike it, follows a change of the phone clock. See [G7Lifecycle.clockAnchor].
     */
    val clockAnchorAt: Long? = null,
    /** Session length from the sensor, seconds, including the 12 hour grace period. */
    val sessionLengthSeconds: Long? = null,
    val warmupSeconds: Int? = null,
    val pairedAt: Long? = null,
    val latestReadingAt: Long? = null,
    val latestGlucose: Int? = null,
    val latestAlgorithmState: Int? = null,
    val latestTrendRate: Double? = null,
    val latestConnectAt: Long? = null,
    /** Last refusal the user can act on; cleared by the next reading. */
    val lastAuthenticationFailure: String? = null,
    val lastAuthenticationFailureAt: Long? = null,
    val calibration: G7CalibrationRecord? = null,
    val previousSensor: G7SensorRecord? = null,
    /** Lifecycle alerts already posted for this sensor, by name, so each is posted once. */
    val alertsIssued: Set<String> = emptySet()
) {

    val isPaired: Boolean get() = address != null

    val canAuthenticate: Boolean get() = sharedKeyHex != null || pairingCode != null

    val model: G7SensorModel? get() = G7SensorModel.fromAdvertisedName(sensorName) ?: sensorName?.let { if (it.startsWith("Dexcom")) G7SensorModel.G7 else null }

    val expiresAt: Long? get() = activatedAt?.let { G7Lifecycle.expiresAt(it, sessionLengthSeconds) }

    val endsAt: Long? get() = activatedAt?.let { G7Lifecycle.endsAt(it, sessionLengthSeconds) }

    val warmupEndsAt: Long? get() = activatedAt?.let { it + G7Lifecycle.warmupMs(warmupSeconds) }

    /** "Known" means a first reading has told us when the sensor started. */
    fun lifecycleState(now: Long): G7LifecycleState =
        if (!isPaired) G7LifecycleState.UNPAIRED
        else G7Lifecycle.state(activatedAt != null, canAuthenticate, activatedAt, sessionLengthSeconds, latestAlgorithmState?.let { AlgorithmState(it) }, now)

    /** This sensor as a record for the "previous sensor" row. */
    fun archived(now: Long, reason: String) = G7SensorRecord(
        sensorName = sensorName,
        serial = serial,
        activatedAt = activatedAt,
        sessionLengthSeconds = sessionLengthSeconds,
        endedAt = now,
        endReason = reason
    )
}

/** A calibration entered in this app, and what the sensor made of it. */
@Serializable
data class G7CalibrationRecord(
    val glucose: Int,
    val enteredAt: Long,
    val outcome: Outcome = Outcome.PENDING,
    val outcomeAt: Long? = null,
    val status: Int? = null,
    val processingStatus: String? = null
) {

    enum class Outcome { PENDING, ACCEPTED, REJECTED }
}

/** The sensor before the current one, for the status screen. */
@Serializable
data class G7SensorRecord(
    val sensorName: String?,
    val serial: String?,
    val activatedAt: Long?,
    val sessionLengthSeconds: Long?,
    val endedAt: Long,
    val endReason: String
)

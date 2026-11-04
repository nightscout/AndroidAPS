package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Master→client mirror of the live bolus-progress state for the bolus a given client initiated, written to
 * the per-client identifier `aaps_clientcontrol_progress_<clientId>` (overwritten in place, throttled to
 * ~1/s). Signed with the same shared HMAC secret as the [AckEnvelope] so the client can prove the progress
 * genuinely came from its paired master (a forged "100% delivered" must not be believable).
 *
 * One-way (master writes, client reads) and best-effort — a dropped frame just means a momentarily stale
 * bar. [phase] tells the client how to drive its OWN `BolusProgressData` so the existing progress dialog
 * lights up unchanged:
 *  - [ProgressPhase.Active]   → start() if idle, then updateProgress(percent, status, delivered)
 *  - [ProgressPhase.Complete] → completeAndAutoClear() (percent reached 100)
 *  - [ProgressPhase.Cleared]  → clear() (bolus ended before 100: failure / cancel)
 *
 * [timestamp] is the master mint time (±5 min skew checked, signed); the client also uses it to drop a
 * late/out-of-order frame.
 */
@Serializable
data class ProgressEnvelope(
    val clientId: String,
    val phase: ProgressPhase,
    val insulin: Double,
    val percent: Int,
    val status: String,
    val delivered: Double,
    val stopDeliveryEnabled: Boolean,
    val timestamp: Long,
    val signature: String
) {

    /** Byte-stable HMAC input over every field except the signature. */
    fun canonicalString(): String =
        "$clientId|$phase|$insulin|$percent|$status|$delivered|$stopDeliveryEnabled|$timestamp"
}

@Serializable
enum class ProgressPhase {

    @SerialName("Active") Active,
    @SerialName("Complete") Complete,
    @SerialName("Cleared") Cleared
}

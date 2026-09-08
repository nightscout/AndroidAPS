package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Master→client acknowledgement for a single client-control command, signed with the same shared
 * HMAC secret as the inbound [SignedEnvelope] so the client can prove the result genuinely came from
 * its paired master (a forged "Ok" must not be able to make a client believe a therapy action applied).
 *
 * Three-phase lifecycle, written to the per-client identifier `aaps_clientcontrol_ack_<clientId>`
 * (overwritten in place):
 * 1. [AckPhase.Executing] / [AckStatus.Pending] — written before dispatch: "received it, applying now".
 * 2. [AckPhase.Done] / [AckStatus.Ok] | [AckStatus.Failed] | [AckStatus.Expired] — the terminal result.
 * 3. [AckPhase.Delivery] / [AckStatus.Failed] — OPTIONAL late relay: a bolus the Done ack reported as queue-accepted
 *    ("Ok") that later FAILED on the pump (the async outcome the round-trip can't wait for). Written after Done, out
 *    of band from the original round-trip; the client turns it into an alarm.
 *
 * [commandCounter] echoes the command envelope's `counter`, the correlation id the client matches
 * against its outstanding request (a stale ack from a previous command carries a different counter
 * and is ignored).
 *
 * [payload] is optional signed result data a command may return to the client — currently the prepared-bolus
 * preview JSON for a `BolusPrepare` ack. It is part of [canonicalString] so a forged dose is no more believable
 * than a forged "Ok".
 */
@Serializable
data class AckEnvelope(
    val clientId: String,
    val commandCounter: Long,
    val phase: AckPhase,
    val status: AckStatus,
    val reason: String? = null,
    val payload: String? = null,
    val timestamp: Long,
    val signature: String
) {

    /**
     * Byte-stable HMAC input over every field except the signature.
     *
     * Note: a null vs. empty [reason]/[payload] collide here (both render as ""), but this is not
     * exploitable — the canonical string is only ever RE-DERIVED from the struct fields to (re)sign
     * or verify, never RE-PARSED back into a struct, so the lost distinction can never change which
     * fields a verified ack carries. Do not change the format (wire contract).
     */
    fun canonicalString(): String =
        "$clientId|$commandCounter|$phase|$status|${reason ?: ""}|${payload ?: ""}|$timestamp"
}

@Serializable
enum class AckPhase {

    @SerialName("Executing") Executing,
    @SerialName("Done") Done,
    @SerialName("Delivery") Delivery
}

@Serializable
enum class AckStatus {

    @SerialName("Pending") Pending,
    @SerialName("Ok") Ok,
    @SerialName("Failed") Failed,
    @SerialName("Expired") Expired
}

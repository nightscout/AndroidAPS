package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * Generic HMAC-signed envelope used for every client→master message
 * (hello, scene.start, scene.stop, …).
 *
 * `payload` is the already-serialized JSON of the inner message — kept as a
 * raw String so signature verification compares the *bytes that travelled the
 * wire*, immune to receiver-side JSON canonicalization differences.
 *
 * Replay protection rests on three fields together:
 * - `clientId` scopes the counter / secret to one pairing
 * - `counter` must be strictly greater than the master's last accepted value
 * - `timestamp` (millis epoch) must be within ±5 min of master clock
 *
 * [validUntil] (millis epoch) is the command's hard expiry: the master must NOT
 * execute a command once `now > validUntil` (it acks `Expired` instead). It is
 * part of [canonicalString] so it cannot be tampered with. A fire-and-forget
 * sender sets it to ~timestamp + skew window (no behaviour change vs. the skew
 * check alone); a round-trip sender sets a short value (e.g. +8 s) and derives
 * its own ACK wait window from it. [Long.MAX_VALUE] means "no expiry".
 *
 * [wantsAck] tells the master to write a two-step ACK doc for this command. Only
 * round-trip senders set it; fire-and-forget commands (scenes, pref sync) leave it
 * false so the master doesn't write ACK docs nobody reads. Signed too, so a client
 * can't be tricked into more/less acking than it asked for.
 *
 * Signature input is the canonical string produced by [canonicalString].
 */
@Serializable
data class SignedEnvelope(
    val clientId: String,
    val counter: Long,
    val timestamp: Long,
    val type: String,
    val payload: String,
    val signature: String,
    val validUntil: Long = Long.MAX_VALUE,
    val wantsAck: Boolean = false
) {

    /**
     * Deterministic byte-stable representation of all fields except the
     * signature itself, used as HMAC input on both sender and receiver.
     */
    fun canonicalString(): String =
        "$clientId|$counter|$timestamp|$validUntil|$wantsAck|$type|$payload"
}

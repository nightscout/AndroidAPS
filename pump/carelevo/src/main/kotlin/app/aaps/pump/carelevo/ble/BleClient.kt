package app.aaps.pump.carelevo.ble

import kotlinx.coroutines.flow.SharedFlow

/**
 * Request/response client over a [app.aaps.pump.carelevo.ble.gatt.GattConnection].
 *
 * Serializes outgoing writes with a mutex so at most one request is in flight at any
 * moment, and correlates each request with the peripheral's notification by opcode
 * pair (and, for the immediate-bolus command, by echoed actionId).
 *
 * Notifications arriving outside any active request are forwarded to
 * [unsolicitedEvents] so alarm/status-report consumers can subscribe independently.
 */
interface BleClient {

    /**
     * Send [cmd], suspend until the matching response notification arrives, then
     * return the decoded response.
     *
     * Does **not** impose a timeout — callers wrap in `withTimeout(...)` if a
     * deadline is wanted. Different pump operations have legitimately different
     * deadlines (e.g. safety-check is ~100 s, most requests are ~3 s), so the
     * policy lives at the caller.
     *
     * Throws:
     * - [BleDisconnectedException] if the GATT connection drops while the request
     *   is pending.
     * - [app.aaps.pump.carelevo.ble.gatt.GattWriteException] if the BLE write fails.
     * - [kotlinx.coroutines.CancellationException] on coroutine cancellation
     *   (including `withTimeout`).
     */
    suspend fun <R : BleResponse> request(cmd: BleCommand<R>): R

    /**
     * Hot stream of notifications that did not match any active request — alarms,
     * status pushes, cannula-insertion events, etc. Subscribers see only events
     * emitted after they subscribe (no replay). Independent from [request] —
     * alarm handling and request/response correlation do not interfere.
     */
    val unsolicitedEvents: SharedFlow<UnsolicitedMessage>
}

/**
 * A single CareLevo protocol request, paired with the Kotlin type of its expected
 * response. Implementations encode the outgoing byte-array, declare the opcode
 * pair, and decode the response bytes back into a typed model.
 */
interface BleCommand<R : BleResponse> {

    /** Opcode byte (position 0) of the outgoing write. */
    val requestOpcode: Byte

    /** Opcode byte (position 0) that the peripheral is expected to reply with. */
    val expectedResponseOpcode: Byte

    /**
     * Optional correlation byte at position 1 of the response. Non-null for commands
     * that echo a caller-chosen identifier (e.g. immediate bolus `actionId`). When
     * non-null, [BleClient] only accepts responses whose byte-1 equals this value —
     * belt-and-braces against a stale or unsolicited message with the same opcode.
     */
    val correlationByte: Byte? get() = null

    /** Full outgoing payload, starting with [requestOpcode] at byte 0. */
    fun encode(): ByteArray

    /** Parse the full response payload (byte 0 is [expectedResponseOpcode]). */
    fun decode(responsePayload: ByteArray): R
}

/** Marker type for decoded CareLevo responses. */
interface BleResponse

/**
 * A notification that did not match any active request — an alarm, status report,
 * or other peripheral-initiated push. Carries the raw payload; higher layers parse
 * it via the existing parser registry.
 */
data class UnsolicitedMessage(
    val opcode: Byte,
    val payload: ByteArray
)

/** Thrown by a pending [BleClient.request] if the GATT connection drops. */
class BleDisconnectedException(message: String = "connection dropped") : RuntimeException(message)

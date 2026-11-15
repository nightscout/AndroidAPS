package app.aaps.pump.carelevo.ble

import kotlinx.coroutines.flow.Flow
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
     * Send [cmd], suspend until **all** of its [BleMultiCommand.expectedResponseOpcodes]
     * have arrived (in any order), then decode them together.
     *
     * For requests the pump answers with more than one notification — e.g. Patch
     * Information Inquiry (`0x33`), answered by RPT1 (`0x93`) + RPT2 (`0x94`). The first
     * notification seen for each expected opcode wins; a duplicate or any non-matching
     * notification (alarm, status push) falls through to [unsolicitedEvents].
     *
     * Same deadline policy as [request] — no built-in timeout, wrap in `withTimeout(...)`.
     * A single deadline wraps the whole call (not per-response).
     *
     * Throws [BleDisconnectedException] / [app.aaps.pump.carelevo.ble.gatt.GattWriteException] /
     * [kotlinx.coroutines.CancellationException] on the same conditions as [request].
     */
    suspend fun <R : BleResponse> requestMultiple(cmd: BleMultiCommand<R>): R

    /**
     * Send [cmd] and return a cold [Flow] of every decoded notification matching
     * [BleStreamCommand.expectedResponseOpcode], completing when [BleStreamCommand.isTerminal]
     * is `true` for a decoded response.
     *
     * For streaming/progress requests — e.g. Safety Check (`0x12`): the pump emits
     * repeated progress reports then a terminal SUCCESS/error, all on `0x72`. The
     * request write happens when the returned flow is collected; the request slot is
     * held for the whole stream (single-in-flight), so a concurrent [request] waits
     * until the stream terminates.
     *
     * The flow throws [BleDisconnectedException] if the link drops mid-stream. As with
     * [request], impose a deadline by wrapping collection in `withTimeout(...)`.
     *
     * **Do not call any [BleClient] method from inside this flow's collector.** The
     * request slot (a non-reentrant mutex) is held for the whole stream, so a reentrant
     * `request`/`requestMultiple`/`requestStream` issued from the `collect {}` block
     * self-deadlocks. Collect the stream to completion first, then issue the next
     * request outside the collector.
     */
    fun <R : BleResponse> requestStream(cmd: BleStreamCommand<R>): Flow<R>

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

/**
 * A CareLevo request that a single write answers with **more than one** notification —
 * e.g. Patch Information Inquiry (`0x33`) → RPT1 (`0x93`) + RPT2 (`0x94`).
 *
 * [BleClient.requestMultiple] collects the first notification seen for each opcode in
 * [expectedResponseOpcodes] (arrival order irrelevant) and completes once every opcode
 * in the set has been received, then hands the collected payloads to [decode].
 */
interface BleMultiCommand<R : BleResponse> {

    /** Opcode byte (position 0) of the outgoing write. */
    val requestOpcode: Byte

    /**
     * The full set of response opcodes this request produces. The request completes
     * only once one notification for **each** opcode has arrived. Must be non-empty.
     */
    val expectedResponseOpcodes: Set<Byte>

    /** Full outgoing payload, starting with [requestOpcode] at byte 0. */
    fun encode(): ByteArray

    /**
     * Parse the collected responses, keyed by their opcode (byte 0 of each payload).
     * Every key in [expectedResponseOpcodes] is guaranteed present.
     */
    fun decode(responses: Map<Byte, ByteArray>): R
}

/**
 * A CareLevo request whose response is a **stream** — repeated notifications on one
 * opcode, terminated by a distinguished response. E.g. Safety Check (`0x12`): the pump
 * emits progress reports (`REP_REQUEST`) then a terminal SUCCESS/error, all on `0x72`.
 *
 * [BleClient.requestStream] decodes each notification matching [expectedResponseOpcode]
 * and emits it; the stream completes when [isTerminal] returns `true` for a decoded
 * response. Non-matching notifications fall through to [BleClient.unsolicitedEvents].
 */
interface BleStreamCommand<R : BleResponse> {

    /** Opcode byte (position 0) of the outgoing write. */
    val requestOpcode: Byte

    /** Opcode byte (position 0) each streamed response notification carries. */
    val expectedResponseOpcode: Byte

    /** Full outgoing payload, starting with [requestOpcode] at byte 0. */
    fun encode(): ByteArray

    /** Parse one streamed response payload (byte 0 is [expectedResponseOpcode]). */
    fun decode(responsePayload: ByteArray): R

    /** `true` when [response] is the final one — the stream completes after emitting it. */
    fun isTerminal(response: R): Boolean
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

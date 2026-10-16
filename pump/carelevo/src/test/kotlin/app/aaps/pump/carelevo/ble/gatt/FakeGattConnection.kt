package app.aaps.pump.carelevo.ble.gatt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/**
 * In-memory [GattConnection] for tests. No Android / Robolectric dependencies.
 *
 * Designed for `runTest` + virtual time — all operations are suspending and play
 * cooperatively with `TestScheduler`. Tests script peripheral behaviour via the
 * test-side API below; the SUT uses only the [GattConnection] surface.
 *
 * Typical shape:
 * ```
 * val gatt = FakeGattConnection()
 * val client = BleClient(gatt)
 *
 * gatt.onNextWrite { w ->
 *     gatt.deliverNotification(NOTIFY_UUID, bolusAckPayload(seq = w.payload[1]))
 * }
 *
 * val ack = client.request(StartBolus(seq = 1, units = 2.5))
 * assertThat(gatt.recordedWrites).hasSize(1)
 * assertThat(gatt.recordedWrites.single().payload).isEqualTo(expectedBytes)
 * ```
 *
 * Gotcha: [Write.equals] uses the default data-class semantics, which for
 * `ByteArray` is reference equality. Compare payload content explicitly with
 * `contentEquals(...)` or assert on individual fields, not the whole `Write`.
 */
class FakeGattConnection : GattConnection {

    private val _events = MutableSharedFlow<GattEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<GattEvent> = _events.asSharedFlow()

    /** A single `writeCharacteristic` invocation, recorded for assertions. */
    data class Write(
        val uuid: UUID,
        val payload: ByteArray,
        val withResponse: Boolean
    )

    private val _writes = mutableListOf<Write>()

    /** Every [writeCharacteristic] call in arrival order. Defensive copy. */
    val recordedWrites: List<Write> get() = _writes.toList()

    private val writeBehaviors = ArrayDeque<suspend (Write) -> Unit>()
    private val writeOutcomes = ArrayDeque<WriteOutcome>()
    private var discoveryOutcome: Boolean = true
    private var closed = false

    // ===== GattConnection implementation =====

    override suspend fun writeCharacteristic(
        uuid: UUID,
        payload: ByteArray,
        withResponse: Boolean
    ) {
        check(!closed) { "FakeGattConnection is closed" }
        val write = Write(uuid, payload, withResponse)
        _writes += write

        // Run scripted side-effect first (typically delivers a notification), then ack.
        // Matches the common case of a pump responding before the BLE stack's ack,
        // but note: real Android BLE makes **no** ordering guarantee between
        // onCharacteristicChanged and onCharacteristicWrite — correctness of
        // [BleClientImpl] must not depend on a specific order. Tests that need the
        // reverse order can emit the notification from the test body after request().
        writeBehaviors.removeFirstOrNull()?.invoke(write)

        val outcome = writeOutcomes.removeFirstOrNull() ?: WriteOutcome.Success
        _events.emit(GattEvent.WriteAck(uuid, ok = outcome is WriteOutcome.Success))
        if (outcome is WriteOutcome.Failure) {
            throw GattWriteException("scripted: ${outcome.reason}")
        }
    }

    override suspend fun discoverServices() {
        check(!closed) { "FakeGattConnection is closed" }
        _events.emit(GattEvent.ServicesDiscovered(discoveryOutcome))
        if (!discoveryOutcome) throw GattDiscoveryException("scripted discovery failure")
    }

    override suspend fun enableNotifications(uuid: UUID) {
        check(!closed) { "FakeGattConnection is closed" }
        // Intentionally a no-op for the fake. Tests deliver notifications via
        // [deliverNotification] regardless of which UUIDs are "enabled".
    }

    override fun close() {
        closed = true
    }

    // ===== Test-side scripting API =====

    /** Emit a notification as if the peripheral sent it. */
    suspend fun deliverNotification(uuid: UUID, payload: ByteArray) {
        _events.emit(GattEvent.Notification(uuid, payload))
    }

    /** Emit a connection-state transition. */
    suspend fun deliverConnectionState(state: GattConnState) {
        _events.emit(GattEvent.ConnectionStateChanged(state))
    }

    /**
     * Run [block] when the next [writeCharacteristic] arrives, after the write is
     * recorded but before the [GattEvent.WriteAck] is emitted. Typical use: `block`
     * calls [deliverNotification] to mimic the pump answering before the ack.
     *
     * Queued FIFO — multiple calls stack up for successive writes.
     */
    fun onNextWrite(block: suspend (Write) -> Unit) {
        writeBehaviors += block
    }

    /**
     * Script the next [writeCharacteristic] call to emit `WriteAck(ok = false)` and
     * then throw [GattWriteException]. Queued FIFO — stacks for successive writes.
     */
    fun scriptNextWriteFailure(reason: String = "scripted") {
        writeOutcomes += WriteOutcome.Failure(reason)
    }

    /**
     * Script [discoverServices] to emit `ServicesDiscovered(ok = false)` and throw
     * [GattDiscoveryException]. Latching — stays in effect until reset with
     * [resetDiscoveryOutcome].
     */
    fun scriptDiscoveryFailure() {
        discoveryOutcome = false
    }

    /** Reset discovery outcome to the default (success). */
    fun resetDiscoveryOutcome() {
        discoveryOutcome = true
    }

    private sealed interface WriteOutcome {
        data object Success : WriteOutcome
        data class Failure(val reason: String) : WriteOutcome
    }
}

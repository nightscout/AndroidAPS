package app.aaps.pump.carelevo.ble.gatt

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Adapts the shared fleet [BleTransport] to carelevo's [GattConnection] contract, so the
 * [app.aaps.pump.carelevo.ble.BleClientImpl] correlation layer runs on the same BLE transport
 * abstraction Dana/Equil/Medtrum use.
 *
 * It registers itself as the transport's single [BleTransportListener] and translates each callback
 * into either:
 * - a per-op [CompletableDeferred] that unblocks a suspend [GattConnection] method, or
 * - a [GattEvent] on the hot [events] flow (for [BleClient]'s notification routing and
 *   connection-state observers).
 *
 * Internally: per-op [CompletableDeferred]s for the suspend ops plus an events-[SharedFlow] fed from
 * the [BleTransportListener] callbacks.
 *
 * **Semantic note (write acks):** [BleTransportListener.onCharacteristicWritten] carries
 * no status, so a write the BLE stack silently drops is NOT fast-failed with [GattWriteException] —
 * it degrades to the caller's `withTimeout(...)`, which every [BleClient.request] already applies.
 * The *not-connected* case is still fast-failed (the transport calls `onConnectionStateChanged(false)`
 * synchronously from the write). This matches the fleet transport behaviour.
 *
 * **Single-characteristic transport:** the shared `BleGatt` has one hardcoded write and one notify
 * characteristic and no write-type parameter, so [writeCharacteristic]'s `withResponse` flag is not
 * honored (writes are always acknowledged) and the `uuid` args are validated against the
 * constructor UUIDs rather than used for routing — a mismatched UUID fails loudly instead of silently
 * targeting the wrong characteristic.
 *
 * Threading: [gattMutex] serializes suspend ops; `CompletableDeferred.complete(...)` is thread-safe
 * and called directly from the (binder thread) listener callbacks. Events are likewise emitted
 * SYNCHRONOUSLY from the callbacks via [emitEvent] (`tryEmit` into the 64-slot buffer) — an async
 * `scope.launch { emit(...) }` detour would open a lost-event window at session teardown: a
 * response arriving just as the per-op timeout cancels [scope] would silently never reach
 * [BleClient]'s router, and a pump that DID execute a dose command would be reported as failed.
 * [scope] is only a fallback for the (pathological) buffer-full case.
 *
 * One adapter instance owns the transport's single listener slot for one connection lifecycle;
 * [close] releases it.
 *
 * @param writeUuid  stamped onto emitted [GattEvent.WriteAck] (the transport has one write char).
 * @param notifyUuid stamped onto emitted [GattEvent.Notification] (the transport has one notify char).
 */
class BleTransportGattConnection(
    private val transport: BleTransport,
    private val writeUuid: UUID,
    private val notifyUuid: UUID,
    private val scope: CoroutineScope
) : GattConnection, BleTransportListener {

    private val _events = MutableSharedFlow<GattEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<GattEvent> = _events.asSharedFlow()

    private val gattMutex = Mutex()

    @Volatile
    private var writeAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var discoveryAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var descriptorAck: CompletableDeferred<Boolean>? = null

    @Volatile
    private var closed = false

    init {
        transport.setListener(this)
    }

    /**
     * Opens the underlying GATT connection to [address]. Not part of [GattConnection] (which models
     * an already-open connection) — callers await [GattEvent.ConnectionStateChanged]`(CONNECTED)` on
     * [events] before calling [discoverServices]. Returns `false` if the BLE stack refused to start
     * the connection (e.g. missing permission or unknown device).
     */
    fun connect(address: String): Boolean = transport.gatt.connect(address)

    // ===== GattConnection =====

    override suspend fun writeCharacteristic(
        uuid: UUID,
        payload: ByteArray,
        withResponse: Boolean
    ) = gattMutex.withLock {
        // Self-guard: after close() the transport listener is null, so the not-connected callback
        // below can no longer abort the deferred — fail fast instead of hanging until the caller's
        // withTimeout.
        if (closed) throw GattWriteException("connection closed")
        require(uuid == writeUuid) { "unexpected write characteristic $uuid; transport has a single write char $writeUuid" }
        val deferred = CompletableDeferred<Boolean>()
        writeAck = deferred
        try {
            // Fire-and-forget on the shared transport; if not connected it synchronously calls
            // onConnectionStateChanged(false), which aborts this deferred → fast-fail preserved.
            transport.gatt.writeCharacteristic(payload)
            val acked = deferred.await()
            if (!acked) throw GattWriteException("onCharacteristicWritten reported failure")
        } finally {
            writeAck = null
        }
    }

    override suspend fun discoverServices() = gattMutex.withLock {
        if (closed) throw GattDiscoveryException("connection closed")
        val deferred = CompletableDeferred<Boolean>()
        discoveryAck = deferred
        try {
            transport.gatt.discoverServices()
            if (!deferred.await()) throw GattDiscoveryException("onServicesDiscovered reported failure")
        } finally {
            discoveryAck = null
        }
    }

    override suspend fun enableNotifications(uuid: UUID) = gattMutex.withLock {
        if (closed) throw GattDiscoveryException("connection closed")
        require(uuid == notifyUuid) { "unexpected notify characteristic $uuid; transport has a single notify char $notifyUuid" }
        val deferred = CompletableDeferred<Boolean>()
        descriptorAck = deferred
        try {
            transport.gatt.enableNotifications()
            if (!deferred.await()) throw GattDiscoveryException("onDescriptorWritten reported failure")
        } finally {
            descriptorAck = null
        }
    }

    override fun close() {
        // Idempotent (GattConnection contract). The flag also makes subsequent suspend ops fail fast
        // rather than hang, since setListener(null) below disables the not-connected abort callback.
        closed = true
        // Abort any in-flight suspending operations first — the transport's close() is not
        // guaranteed to produce a final onConnectionStateChanged on every chipset.
        writeAck?.completeExceptionally(GattWriteException("connection closed"))
        discoveryAck?.completeExceptionally(GattDiscoveryException("connection closed"))
        descriptorAck?.completeExceptionally(GattDiscoveryException("connection closed"))
        transport.gatt.close()
        transport.setListener(null)
    }

    // ===== BleTransportListener =====

    /**
     * Delivers [event] to [events] synchronously on the calling (binder) thread. Falls back to an
     * async emit only if the buffer is full — see the class-level threading note for why the
     * synchronous path is load-bearing.
     */
    private fun emitEvent(event: GattEvent) {
        if (!_events.tryEmit(event)) scope.launch { _events.emit(event) }
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        val state = if (connected) GattConnState.CONNECTED else GattConnState.DISCONNECTED
        emitEvent(GattEvent.ConnectionStateChanged(state))
        if (!connected) {
            // Connection gone — abort any in-flight operations (BleClient aborts its own request
            // waiter separately off the DISCONNECTED event).
            writeAck?.completeExceptionally(GattWriteException("disconnected"))
            discoveryAck?.completeExceptionally(GattDiscoveryException("disconnected"))
            descriptorAck?.completeExceptionally(GattDiscoveryException("disconnected"))
        }
    }

    override fun onServicesDiscovered(success: Boolean) {
        discoveryAck?.complete(success)
        emitEvent(GattEvent.ServicesDiscovered(success))
    }

    override fun onDescriptorWritten() {
        // Transport only invokes this on GATT_SUCCESS; a failed CCCD write produces no callback and
        // is surfaced by the caller's withTimeout (mirrors the fleet transport behaviour).
        descriptorAck?.complete(true)
    }

    override fun onCharacteristicChanged(data: ByteArray) {
        emitEvent(GattEvent.Notification(notifyUuid, data.copyOf()))
    }

    override fun onCharacteristicWritten() {
        // No status on the shared listener — a delivered write-ack is treated as success.
        writeAck?.complete(true)
        emitEvent(GattEvent.WriteAck(writeUuid, true))
    }
}

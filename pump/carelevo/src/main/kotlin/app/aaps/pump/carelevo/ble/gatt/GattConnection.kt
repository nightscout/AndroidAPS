package app.aaps.pump.carelevo.ble.gatt

import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

/**
 * Thin abstraction over the Android `BluetoothGatt` lifecycle for a single peripheral.
 *
 * The concrete production implementation wraps `BluetoothGatt` + `BluetoothGattCallback`
 * and bridges the callback into a hot [SharedFlow] of [GattEvent]s. Tests substitute
 * `FakeGattConnection` to script BLE behaviour without Android types.
 *
 * Designed as the single chokepoint through which all BLE I/O for the CareLevo driver
 * flows. Callers above this layer never reference `BluetoothGatt` directly.
 */
interface GattConnection {

    /**
     * Hot, multicast stream of GATT events as the BLE stack reports them.
     *
     * Subscribers receive only events emitted after they subscribe (no replay).
     * Use [BleClient]-style suspend operations for request/response correlation —
     * direct subscription to this flow is intended for connection-state observers
     * and unsolicited notifications (alarms, status pushes).
     */
    val events: SharedFlow<GattEvent>

    /**
     * Writes [payload] to the characteristic identified by [uuid].
     *
     * Suspends until the BLE stack delivers a matching [GattEvent.WriteAck], then returns
     * normally on success or throws [GattWriteException] on failure. Does not impose
     * its own timeout — wrap the call in `withTimeout(...)` if a deadline is needed.
     *
     * @param withResponse `true` selects acknowledged write (`WRITE_TYPE_DEFAULT`),
     *                     `false` selects fire-and-forget (`WRITE_TYPE_NO_RESPONSE`).
     */
    suspend fun writeCharacteristic(
        uuid: UUID,
        payload: ByteArray,
        withResponse: Boolean = true
    )

    /**
     * Initiates service discovery and suspends until the BLE stack reports completion.
     * Throws [GattDiscoveryException] if the stack reports failure.
     */
    suspend fun discoverServices()

    /**
     * Subscribes to peripheral-initiated notifications/indications on [uuid].
     * Subsequent [GattEvent.Notification] events for [uuid] will appear in [events].
     */
    suspend fun enableNotifications(uuid: UUID)

    /**
     * Releases the underlying `BluetoothGatt` and aborts any in-flight suspending
     * operations with their typed exceptions (`GattWriteException` or
     * `GattDiscoveryException`). The [events] flow remains active for any existing
     * subscribers — callers relying on flow completion should instead cancel the
     * scope that owns the subscription. Idempotent — safe to call repeatedly.
     */
    fun close()
}

/**
 * Anything the GATT layer reports asynchronously. Sealed so consumers must handle
 * every case exhaustively.
 */
sealed interface GattEvent {

    /** A peripheral-initiated notification or indication. */
    data class Notification(
        val uuid: UUID,
        val payload: ByteArray
    ) : GattEvent

    /** Connection state transitioned. */
    data class ConnectionStateChanged(
        val state: GattConnState
    ) : GattEvent

    /**
     * Service discovery finished. [ok] is `true` only when the stack reported
     * `GATT_SUCCESS`; consumers should also re-check the discovered service set
     * matches what the protocol expects before proceeding.
     */
    data class ServicesDiscovered(
        val ok: Boolean
    ) : GattEvent

    /** Acknowledgement of a previously requested write. */
    data class WriteAck(
        val uuid: UUID,
        val ok: Boolean
    ) : GattEvent
}

/**
 * Coarse connection state derived from `BluetoothProfile.STATE_*` and the gatt's
 * disconnect/close lifecycle.
 */
enum class GattConnState {

    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED
}

class GattWriteException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class GattDiscoveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

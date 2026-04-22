package app.aaps.pump.carelevo.ble

import app.aaps.pump.carelevo.ble.gatt.GattConnState
import app.aaps.pump.carelevo.ble.gatt.GattConnection
import app.aaps.pump.carelevo.ble.gatt.GattEvent
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
 * Production implementation of [BleClient].
 *
 * Correlation rules (see [BleClientContractTest] in tests for the full spec):
 * - A single [requestMutex] serializes outgoing requests — at most one in flight.
 * - Before calling [GattConnection.writeCharacteristic], the client registers a
 *   [Waiter] holding the expected response opcode (and optional [BleCommand.correlationByte]).
 *   This ordering eliminates the response-during-write race.
 * - A long-lived collector on the injected [scope] reads [GattConnection.events] and
 *   routes each [GattEvent.Notification] to either the active [Waiter] (when opcode +
 *   correlation match) or to [_unsolicitedEvents] otherwise.
 * - A [GattEvent.ConnectionStateChanged] with [GattConnState.DISCONNECTED] aborts any
 *   pending waiter with [BleDisconnectedException].
 */
class BleClientImpl(
    private val gatt: GattConnection,
    private val writeUuid: UUID,
    @Suppress("unused")
    private val notifyUuid: UUID,
    scope: CoroutineScope
) : BleClient {

    private val _unsolicitedEvents = MutableSharedFlow<UnsolicitedMessage>(extraBufferCapacity = 16)
    override val unsolicitedEvents: SharedFlow<UnsolicitedMessage> = _unsolicitedEvents.asSharedFlow()

    private val requestMutex = Mutex()

    @Volatile
    private var waiter: Waiter? = null

    private data class Waiter(
        val expectedOpcode: Byte,
        val correlationByte: Byte?,
        val deferred: CompletableDeferred<ByteArray>
    )

    init {
        scope.launch {
            gatt.events.collect { onEvent(it) }
        }
    }

    private suspend fun onEvent(evt: GattEvent) {
        when (evt) {
            is GattEvent.Notification           -> routeNotification(evt.payload)

            is GattEvent.ConnectionStateChanged -> {
                if (evt.state == GattConnState.DISCONNECTED) {
                    // Clear the waiter immediately so any late-arriving notification
                    // falls through to _unsolicitedEvents rather than being dropped
                    // against an already-completed deferred.
                    val w = waiter
                    waiter = null
                    w?.deferred?.completeExceptionally(BleDisconnectedException())
                }
            }

            is GattEvent.ServicesDiscovered,
            is GattEvent.WriteAck               -> Unit
        }
    }

    private suspend fun routeNotification(payload: ByteArray) {
        if (payload.isEmpty()) return
        val opcode = payload[0]
        val w = waiter
        if (w != null && opcode == w.expectedOpcode) {
            val correlationOk = w.correlationByte == null ||
                (payload.size > 1 && payload[1] == w.correlationByte)
            if (correlationOk) {
                w.deferred.complete(payload)
                return
            }
        }
        _unsolicitedEvents.emit(UnsolicitedMessage(opcode, payload))
    }

    override suspend fun <R : BleResponse> request(cmd: BleCommand<R>): R = requestMutex.withLock {
        val deferred = CompletableDeferred<ByteArray>()
        // Register the waiter BEFORE writing so a synchronous response from the
        // peripheral cannot race ahead of our subscription.
        waiter = Waiter(cmd.expectedResponseOpcode, cmd.correlationByte, deferred)
        try {
            gatt.writeCharacteristic(writeUuid, cmd.encode())
            val payload = deferred.await()
            cmd.decode(payload)
        } finally {
            waiter = null
        }
    }
}

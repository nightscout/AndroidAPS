package app.aaps.pump.carelevo.ble

import app.aaps.pump.carelevo.ble.gatt.GattConnState
import app.aaps.pump.carelevo.ble.gatt.GattConnection
import app.aaps.pump.carelevo.ble.gatt.GattEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Production implementation of [BleClient].
 *
 * Correlation rules (see [BleClientContractTest] in tests for the full spec):
 * - A single [requestMutex] serializes outgoing requests — at most one in flight,
 *   whether single-response, multi-response, or streaming.
 * - Before calling [GattConnection.writeCharacteristic], the client registers a
 *   [Waiter] describing the response(s) it expects. This ordering eliminates the
 *   response-during-write race.
 * - A long-lived collector on the injected [scope] reads [GattConnection.events] and
 *   offers each [GattEvent.Notification] to the active [Waiter]; anything the waiter
 *   does not consume is forwarded to [_unsolicitedEvents].
 * - A [GattEvent.ConnectionStateChanged] with [GattConnState.DISCONNECTED] aborts the
 *   pending waiter (with [BleDisconnectedException]) atomically.
 *
 * The event collector is the single point through which every request completes, so it
 * is hardened to survive any callback failure: the [waiter] is held in an
 * [AtomicReference] (so a disconnect can atomically take-and-abort whatever request is
 * current without clobbering a concurrently-registered one), consumer-supplied
 * [BleStreamCommand.decode]/[BleStreamCommand.isTerminal] are guarded, and the collect
 * body is wrapped so no unforeseen throw can permanently stop routing.
 */
class BleClientImpl(
    private val gatt: GattConnection,
    private val writeUuid: UUID,
    @Suppress("unused")
    private val notifyUuid: UUID,
    scope: CoroutineScope
) : BleClient {

    // DROP_OLDEST + tryEmit: unsolicited fan-out (alarms/status are lossy pushes) can
    // never back-pressure the event collector and stall in-flight request correlation.
    private val _unsolicitedEvents = MutableSharedFlow<UnsolicitedMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val unsolicitedEvents: SharedFlow<UnsolicitedMessage> = _unsolicitedEvents.asSharedFlow()

    private val requestMutex = Mutex()

    /**
     * The response(s) the in-flight request is waiting for. Exactly one is active at a
     * time (guarded by [requestMutex] on the caller side). Held in an [AtomicReference]
     * because the event collector aborts it on disconnect **outside** the mutex (a
     * stream holds the mutex for its whole 100-210 s duration, so the collector must
     * stay lock-free): [AtomicReference.getAndSet] takes-and-nulls atomically so a
     * disconnect can never null a freshly-registered waiter without also aborting it.
     */
    private val waiterRef = AtomicReference<Waiter?>(null)

    private sealed interface Waiter {

        /** Try to consume the notification [payload] (opcode is [opcode]). Return `true` iff consumed. */
        fun offer(opcode: Byte, payload: ByteArray): Boolean

        /** Abort this pending request (link dropped, or an unforeseen router failure). */
        fun abort(cause: Throwable)

        /**
         * Called by the requesting coroutine when it unwinds WITHOUT having consumed the
         * response (cancellation/timeout). After this returns, [offer] consumes nothing
         * more, so late frames fall through to [unsolicitedEvents]. Returns any frame(s)
         * this waiter had already absorbed but never delivered to the caller, so the
         * caller can re-route them to unsolicited instead of silently swallowing a
         * response the pump really sent (e.g. the ack of a bolus that DID start while
         * our side timed out).
         */
        fun abandon(): List<ByteArray>
    }

    private class SingleWaiter(
        private val expectedOpcode: Byte,
        private val correlationByte: Byte?,
        private val deferred: CompletableDeferred<ByteArray>
    ) : Waiter {

        override fun offer(opcode: Byte, payload: ByteArray): Boolean {
            if (opcode != expectedOpcode) return false
            val correlationOk = correlationByte == null ||
                (payload.size > 1 && payload[1] == correlationByte)
            if (!correlationOk) return false
            // complete() returns false if already settled — a late duplicate (already
            // completed) or an abandoned caller (deferred cancelled by abandon()) — so
            // routeNotification forwards the frame to unsolicited either way.
            return deferred.complete(payload)
        }

        override fun abort(cause: Throwable) {
            deferred.completeExceptionally(cause)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun abandon(): List<ByteArray> {
            // cancel() races atomically against offer()'s complete(): exactly one wins.
            // If cancel wins, a concurrent/late offer returns false and the frame is
            // routed to unsolicited by the collector. If complete already won, the
            // payload sits in a deferred nobody will await — hand it back for re-routing.
            deferred.cancel()
            return if (deferred.isCompleted && !deferred.isCancelled) listOf(deferred.getCompleted()) else emptyList()
        }
    }

    private class MultiWaiter(
        private val remaining: MutableSet<Byte>,
        private val deferred: CompletableDeferred<Map<Byte, ByteArray>>
    ) : Waiter {

        private val collected = mutableMapOf<Byte, ByteArray>()

        override fun offer(opcode: Byte, payload: ByteArray): Boolean {
            // Abandoned by the caller — stop consuming so frames route to unsolicited.
            // (offer runs only on the single collector coroutine; isCancelled is the
            // cross-thread signal from abandon(). A frame absorbed into `collected` in
            // the narrow window before cancel lands is dropped with its partial map —
            // a partial multi-response is unusable anyway and pairing redoes the round.)
            if (deferred.isCancelled) return false
            // First notification per expected opcode wins; a duplicate opcode is no
            // longer in `remaining`, so it falls through to unsolicited.
            if (!remaining.remove(opcode)) return false
            collected[opcode] = payload
            if (remaining.isEmpty() && !deferred.complete(collected.toMap())) return false
            return true
        }

        override fun abort(cause: Throwable) {
            deferred.completeExceptionally(cause)
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun abandon(): List<ByteArray> {
            deferred.cancel()
            return if (deferred.isCompleted && !deferred.isCancelled) deferred.getCompleted().values.toList() else emptyList()
        }
    }

    private class StreamWaiter<R : BleResponse>(
        private val expectedOpcode: Byte,
        private val decode: (ByteArray) -> R,
        private val isTerminal: (R) -> Boolean,
        private val channel: Channel<R>
    ) : Waiter {

        override fun offer(opcode: Byte, payload: ByteArray): Boolean {
            if (opcode != expectedOpcode) return false
            // Both consumer-supplied callbacks run here on the shared event collector,
            // so both are guarded: a throw must end THIS stream, never escape onto the
            // collector and brick routing for every future request.
            val decoded = try {
                decode(payload)
            } catch (t: Throwable) {
                channel.close(t)
                return true
            }
            // Channel is UNLIMITED, so a success means it was accepted; a failure means
            // it is already closed (terminal already delivered) — treat a late same-opcode
            // frame as not-consumed so it falls through to unsolicited.
            if (!channel.trySend(decoded).isSuccess) return false
            val terminal = try {
                isTerminal(decoded)
            } catch (t: Throwable) {
                channel.close(t)
                return true
            }
            if (terminal) channel.close()
            return true
        }

        override fun abort(cause: Throwable) {
            channel.close(cause)
        }

        override fun abandon(): List<ByteArray> {
            // cancel() (not close()) so a concurrent trySend fails and the frame routes
            // to unsolicited. Frames already decoded into the channel are typed R, not
            // raw bytes — nothing to re-route; stream frames are progress reports whose
            // loss the stream consumer already treats as a failed operation.
            channel.cancel()
            return emptyList()
        }
    }

    init {
        scope.launch {
            gatt.events.collect { evt ->
                try {
                    onEvent(evt)
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    // The sole event router must never die. Callback throws are already
                    // guarded above; this is a last-resort backstop — abort the active
                    // request so its caller fails fast rather than hanging on a response
                    // that will never route, and keep the collector alive.
                    waiterRef.getAndSet(null)?.abort(t)
                }
            }
        }
    }

    private fun onEvent(evt: GattEvent) {
        when (evt) {
            is GattEvent.Notification           -> routeNotification(evt.payload)

            is GattEvent.ConnectionStateChanged -> {
                if (evt.state == GattConnState.DISCONNECTED) {
                    // Atomically take-and-null so a late-arriving notification falls
                    // through to unsolicited rather than hitting an aborted waiter.
                    waiterRef.getAndSet(null)?.abort(BleDisconnectedException())
                }
            }

            is GattEvent.ServicesDiscovered,
            is GattEvent.WriteAck               -> Unit
        }
    }

    private fun routeNotification(payload: ByteArray) {
        if (payload.isEmpty()) return
        val opcode = payload[0]
        if (waiterRef.get()?.offer(opcode, payload) == true) return
        // tryEmit never suspends (DROP_OLDEST) so the collector can't be back-pressured.
        _unsolicitedEvents.tryEmit(UnsolicitedMessage(opcode, payload))
    }

    /**
     * Tears down [waiter] after its requesting coroutine unwound without consuming the
     * response (cancellation/timeout), re-routing any frame the waiter had already
     * absorbed to [unsolicitedEvents] — see [Waiter.abandon].
     */
    private fun abandonAndReroute(waiter: Waiter) {
        for (frame in waiter.abandon()) {
            if (frame.isNotEmpty()) _unsolicitedEvents.tryEmit(UnsolicitedMessage(frame[0], frame))
        }
    }

    override suspend fun <R : BleResponse> request(cmd: BleCommand<R>): R = requestMutex.withLock {
        val deferred = CompletableDeferred<ByteArray>()
        // Register the waiter BEFORE writing so a synchronous response from the
        // peripheral cannot race ahead of our subscription.
        val waiter = SingleWaiter(cmd.expectedResponseOpcode, cmd.correlationByte, deferred)
        waiterRef.set(waiter)
        var consumed = false
        try {
            gatt.writeCharacteristic(writeUuid, cmd.encode())
            val payload = deferred.await()
            consumed = true
            cmd.decode(payload)
        } finally {
            // compareAndSet: never clobber a successor registered after a disconnect
            // already took-and-aborted this waiter.
            waiterRef.compareAndSet(waiter, null)
            if (!consumed) abandonAndReroute(waiter)
        }
    }

    override suspend fun <R : BleResponse> requestMultiple(cmd: BleMultiCommand<R>): R =
        requestMutex.withLock {
            require(cmd.expectedResponseOpcodes.isNotEmpty()) {
                "expectedResponseOpcodes must not be empty"
            }
            val deferred = CompletableDeferred<Map<Byte, ByteArray>>()
            val waiter = MultiWaiter(cmd.expectedResponseOpcodes.toMutableSet(), deferred)
            waiterRef.set(waiter)
            var consumed = false
            try {
                gatt.writeCharacteristic(writeUuid, cmd.encode())
                val responses = deferred.await()
                consumed = true
                cmd.decode(responses)
            } finally {
                waiterRef.compareAndSet(waiter, null)
                if (!consumed) abandonAndReroute(waiter)
            }
        }

    override fun <R : BleResponse> requestStream(cmd: BleStreamCommand<R>): Flow<R> = flow {
        requestMutex.withLock {
            val channel = Channel<R>(Channel.UNLIMITED)
            // Register BEFORE writing (same race-free ordering as request()).
            val waiter = StreamWaiter(cmd.expectedResponseOpcode, cmd::decode, cmd::isTerminal, channel)
            waiterRef.set(waiter)
            try {
                gatt.writeCharacteristic(writeUuid, cmd.encode())
                // Emits every decoded notification; the channel is closed by the
                // StreamWaiter on the terminal response, on decode/isTerminal failure
                // (rethrown here as the close cause), or on disconnect.
                for (item in channel) {
                    emit(item)
                }
            } finally {
                waiterRef.compareAndSet(waiter, null)
                // Always abandon: a no-op after normal completion (channel already
                // closed), and after cancellation it flips late frames to unsolicited.
                waiter.abandon()
            }
        }
    }
}

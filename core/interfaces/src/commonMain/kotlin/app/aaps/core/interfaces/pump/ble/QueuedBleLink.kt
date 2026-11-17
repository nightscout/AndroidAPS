package app.aaps.core.interfaces.pump.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * What a platform does for one connection, for [QueuedBleLink]. It only starts things on the Bluetooth stack; the rules
 * of [BleLink] are kept by [QueuedBleLink], the same on every platform.
 *
 * Each `start` function begins one operation and returns false when the stack refused to begin it; no end is reported
 * then. An operation that began ends with the matching call on the link: [QueuedBleLink.writeDone],
 * [QueuedBleLink.readDone], [QueuedBleLink.subscribeDone] or [QueuedBleLink.mtuDone]. The end may come on any thread,
 * and also at once, inside the start call.
 */
interface BleLinkPort {

    fun startWrite(characteristic: CharacteristicId, data: ByteArray, type: WriteType): Boolean
    fun startRead(characteristic: CharacteristicId): Boolean

    /** [kind] is [Subscription.NOTIFY] or [Subscription.INDICATE], never [Subscription.AUTO]. */
    fun startSubscribe(characteristic: CharacteristicId, kind: Subscription): Boolean
    fun startMtu(wanted: Int): Boolean
    fun maxWriteLength(type: WriteType): Int
    fun requestPriority(priority: LinkPriority)

    /** Ends the connection and frees what the platform holds for it. Called once, when the link dies. */
    fun release()
}

/**
 * The [BleLink] of every platform: the Android and the iOS central and the emulated one give this class, each with its
 * own [BleLinkPort]. So the operation queue, the time limits and the end of a link are one code, tested once.
 *
 * An operation whose caller stopped waiting (its coroutine was cancelled) still holds the Bluetooth stack. The next
 * operation waits for its end, until the time limit of the first one.
 */
class QueuedBleLink(
    override val device: DeviceId,
    override val services: GattServices,
    private val port: BleLinkPort,
    private val aapsLogger: AAPSLogger? = null
) : BleLink {

    private val _state = MutableStateFlow<LinkState>(LinkState.Up)
    override val state: StateFlow<LinkState> = _state

    private val received = Channel<Incoming>(Channel.UNLIMITED)
    override val incoming: Flow<Incoming> = received.receiveAsFlow()

    private val operations = Mutex()

    /** The operation on the stack now. A [MutableStateFlow] only for its atomic compareAndSet. */
    private val running = MutableStateFlow<Running?>(null)

    override fun maxWriteLength(type: WriteType): Int = port.maxWriteLength(type)

    override suspend fun subscribe(characteristic: CharacteristicId, kind: Subscription, timeout: Duration): BleResult<Unit> {
        val properties = services.characteristics[characteristic] ?: return notFound(characteristic)
        val wanted = when (kind) {
            Subscription.AUTO     -> when {
                CharacteristicProperty.INDICATE in properties -> Subscription.INDICATE
                CharacteristicProperty.NOTIFY in properties   -> Subscription.NOTIFY
                else                                          -> return notAllowed(characteristic, "notify")
            }

            Subscription.NOTIFY   -> if (CharacteristicProperty.NOTIFY in properties) kind else return notAllowed(characteristic, "notify")
            Subscription.INDICATE -> if (CharacteristicProperty.INDICATE in properties) kind else return notAllowed(characteristic, "indicate")
        }
        return perform(Operation.SUBSCRIBE, characteristic, timeout) { port.startSubscribe(characteristic, wanted) }
    }

    override suspend fun write(characteristic: CharacteristicId, data: ByteArray, type: WriteType, timeout: Duration): BleResult<Unit> {
        val properties = services.characteristics[characteristic] ?: return notFound(characteristic)
        if (CharacteristicProperty.WRITE !in properties && CharacteristicProperty.WRITE_WITHOUT_RESPONSE !in properties) return notAllowed(characteristic, "write")
        val longest = port.maxWriteLength(type)
        if (data.size > longest) return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "${data.size} bytes, but only $longest fit in one write"))
        // A copy: the caller may change its array while the stack still sends it.
        val bytes = data.copyOf()
        return perform(Operation.WRITE, characteristic, timeout) { port.startWrite(characteristic, bytes, type) }
    }

    override suspend fun read(characteristic: CharacteristicId, timeout: Duration): BleResult<ByteArray> {
        val properties = services.characteristics[characteristic] ?: return notFound(characteristic)
        if (CharacteristicProperty.READ !in properties) return notAllowed(characteristic, "read")
        return perform(Operation.READ, characteristic, timeout) { port.startRead(characteristic) }
    }

    override suspend fun requestMtu(wanted: Int): Int =
        when (val result = perform<Int>(Operation.MTU, null, BleLink.OPERATION_TIMEOUT) { port.startMtu(wanted) }) {
            is BleResult.Ok     -> result.value
            is BleResult.Failed -> port.maxWriteLength(WriteType.WITHOUT_RESPONSE) + ATT_HEADER
        }

    override suspend fun requestPriority(priority: LinkPriority) {
        operations.withLock { if (_state.value == LinkState.Up) port.requestPriority(priority) }
    }

    override suspend fun close() {
        if (end(LinkState.Closed)) log("closed")
    }

    // ---- What the platform reports ------------------------------------------------------------------------------

    fun writeDone(characteristic: CharacteristicId, failure: BleFailure?) =
        finish(Operation.WRITE, characteristic, if (failure == null) BleResult.Ok(Unit) else BleResult.Failed(failure))

    fun readDone(characteristic: CharacteristicId, value: ByteArray?, failure: BleFailure?) =
        finish(
            Operation.READ, characteristic,
            when {
                failure != null -> BleResult.Failed(failure)
                value == null   -> BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "a read with no value"))
                else            -> BleResult.Ok(value)
            }
        )

    fun subscribeDone(characteristic: CharacteristicId, failure: BleFailure?) =
        finish(Operation.SUBSCRIBE, characteristic, if (failure == null) BleResult.Ok(Unit) else BleResult.Failed(failure))

    fun mtuDone(mtu: Int, failure: BleFailure?) =
        finish(Operation.MTU, null, if (failure == null) BleResult.Ok(mtu) else BleResult.Failed(failure))

    /** Data the device sent. Kept until the driver collects it; dropped when the link is dead. */
    fun received(characteristic: CharacteristicId, data: ByteArray) {
        if (_state.value == LinkState.Up) received.trySend(Incoming(characteristic, data))
    }

    /** The connection was lost. Safe to call more than once, and after [close]. */
    fun lost(failure: BleFailure) {
        if (end(LinkState.Lost(failure))) log("lost: $failure")
    }

    // ---- Inside ---------------------------------------------------------------------------------------------------

    private enum class Operation { WRITE, READ, SUBSCRIBE, MTU }

    private class Running(val operation: Operation, val characteristic: CharacteristicId?, val deadline: TimeMark) {

        val result = CompletableDeferred<BleResult<Any>>()
    }

    private suspend fun <T> perform(operation: Operation, characteristic: CharacteristicId?, timeout: Duration, start: () -> Boolean): BleResult<T> =
        operations.withLock {
            running.value?.let { earlier ->
                // A caller that stopped waiting left its operation on the stack. Wait for its end before the next one.
                val left = -earlier.deadline.elapsedNow()
                val ended = earlier.result.isCompleted || withTimeoutOrNull(left) { earlier.result.await() } != null
                if (!ended) stuck(earlier.operation)
            }
            if (_state.value != LinkState.Up) return noLink()
            val now = Running(operation, characteristic, TimeSource.Monotonic.markNow() + timeout)
            running.value = now
            // The link may have died between the check above and this operation becoming visible to end().
            if (_state.value != LinkState.Up) {
                running.compareAndSet(now, null)
                return noLink()
            }
            val started = try {
                start()
            } catch (e: Exception) {
                aapsLogger?.error(LTag.PUMPBTCOMM, "BLE $device: $operation failed to start", e)
                false
            }
            if (!started) {
                running.compareAndSet(now, null)
                return BleResult.Failed(BleFailure(BleFailure.Kind.REFUSED, message = "the Bluetooth stack did not start $operation"))
            }
            val result = withTimeoutOrNull(timeout) { now.result.await() }
            if (result == null) {
                stuck(operation)
                return BleResult.Failed(BleFailure(BleFailure.Kind.TIMEOUT, message = "no end of $operation in $timeout"))
            }
            @Suppress("UNCHECKED_CAST")
            result as BleResult<T>
        }

    /** A stack that does not end one operation does not end the next one either. */
    private fun stuck(operation: Operation) =
        lost(BleFailure(BleFailure.Kind.TIMEOUT, message = "the Bluetooth stack did not end $operation"))

    private fun finish(operation: Operation, characteristic: CharacteristicId?, result: BleResult<Any>) {
        val now = running.value
        if (now == null || now.operation != operation || now.characteristic != characteristic) {
            log("an end of $operation on $characteristic that nobody waits for")
            return
        }
        if (running.compareAndSet(now, null)) now.result.complete(result)
    }

    /** @return true when this call ended the link */
    private fun end(newState: LinkState): Boolean {
        while (true) {
            val current = _state.value
            if (current != LinkState.Up) return false
            if (_state.compareAndSet(current, newState)) break
        }
        val failure = (newState as? LinkState.Lost)?.failure
        while (true) {
            val now = running.value ?: break
            if (running.compareAndSet(now, null)) {
                now.result.complete(BleResult.Failed(BleFailure(BleFailure.Kind.NO_LINK, failure?.platformStatus, "the link ended during ${now.operation}")))
                break
            }
        }
        received.close()
        try {
            port.release()
        } catch (e: Exception) {
            aapsLogger?.error(LTag.PUMPBTCOMM, "BLE $device: release failed", e)
        }
        return true
    }

    private fun <T> noLink(): BleResult<T> = BleResult.Failed(BleFailure(BleFailure.Kind.NO_LINK, message = "the link is ${_state.value}"))

    private fun <T> notFound(characteristic: CharacteristicId): BleResult<T> =
        BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "the device has no $characteristic"))

    private fun <T> notAllowed(characteristic: CharacteristicId, what: String): BleResult<T> =
        BleResult.Failed(BleFailure(BleFailure.Kind.NOT_SUPPORTED, message = "$characteristic does not allow $what"))

    private fun log(message: String) = aapsLogger?.debug(LTag.PUMPBTCOMM, "BLE $device: $message")

    private companion object {

        /** The part of an ATT packet that is not data. */
        const val ATT_HEADER = 3
    }
}

package info.nightscout.comboctl.base.testUtils

import info.nightscout.comboctl.base.Cipher
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.Nonce
import info.nightscout.comboctl.base.TransportLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail

// Utility function to combine runBlocking() with a watchdog.
// A coroutine is started with runBlocking(), and inside that
// coroutine, sub-coroutines are spawned. One of them runs
// the supplied block, the other implements a watchdog by
// waiting with delay(). If delay() runs out, the watchdog
// is considered to have timed out, and failure is reported.
// The watchdog is disabled after the supplied block finished
// running. That way, if something in that block suspends
// coroutines indefinitely, the watchdog will make sure that
// the test does not hang permanently.
fun runBlockingWithWatchdog(
    timeout: Long,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    runBlocking(context) {
        val watchdogJob = launch {
            delay(timeout)
            fail("Test run timeout reached")
        }

        launch {
            try {
                // Call the block with the current CoroutineScope
                // as the receiver to allow code inside that block
                // to access the CoroutineScope via the "this" value.
                // This is important, otherwise test code cannot
                // launch coroutines easily.
                this.block()
            } finally {
                // Disabling the watchdog here makes sure
                // that it is disabled no matter if the block
                // finishes regularly or due to an exception.
                watchdogJob.cancel()
            }
        }
    }
}

class WatchdogTimeoutException(message: String) : ComboException(message)

suspend fun coroutineScopeWithWatchdog(
    timeout: Long,
    block: suspend CoroutineScope.() -> Unit
) {
    coroutineScope {
        val watchdogJob = launch {
            delay(timeout)
            throw WatchdogTimeoutException("Test run timeout reached")
        }

        launch {
            try {
                // Call the block with the current CoroutineScope
                // as the receiver to allow code inside that block
                // to access the CoroutineScope via the "this" value.
                // This is important, otherwise test code cannot
                // launch coroutines easily.
                this.block()
            } finally {
                // Disabling the watchdog here makes sure
                // that it is disabled no matter if the block
                // finishes regularly or due to an exception.
                watchdogJob.cancel()
            }
        }
    }
}

fun produceTpLayerPacket(outgoingPacketInfo: TransportLayer.OutgoingPacketInfo, cipher: Cipher): TransportLayer.Packet {
    val packet = TransportLayer.Packet(
        command = outgoingPacketInfo.command,
        sequenceBit = false,
        reliabilityBit = false,
        address = 0x01,
        nonce = Nonce.nullNonce(),
        payload = outgoingPacketInfo.payload
    )

    packet.authenticate(cipher)

    return packet
}

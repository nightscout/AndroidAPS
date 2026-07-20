package info.nightscout.comboctl.base.testUtils

import info.nightscout.comboctl.base.Cipher
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.base.Nonce
import info.nightscout.comboctl.base.TransportLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.fail

// The watchdog is a hang-guard, not a performance assertion, so its timeout can be widened uniformly
// on a slow/loaded machine without weakening what the tests check. On CI these tests run concurrently
// with two device emulators, and the long-press coroutine tests get starved far past the developer-box
// budget — so allow the environment to scale every watchdog via COMBOCTL_WATCHDOG_SCALE (default 1 =
// unchanged locally; CI sets it higher). See .circleci/config.yml.
private val watchdogScale: Long =
    System.getenv("COMBOCTL_WATCHDOG_SCALE")?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L

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
        val effectiveTimeout = timeout * watchdogScale
        lateinit var blockJob: Job
        val watchdogJob = launch {
            delay(effectiveTimeout)
            // On timeout, dump what is still alive under the block so a flaky hang points at the
            // stuck coroutine instead of only saying "timeout reached". The long-press flake hangs
            // on rtButtonConfirmationBarrier.receive(); pairing this with the [RTBARRIER] logs shows
            // whether a confirmation was sent-but-missed or never sent at all.
            val active = blockJob.children.toList()
            println("[WATCHDOG] timeout after ${effectiveTimeout}ms; blockJob active=${blockJob.isActive} children=${active.size}")
            active.forEachIndexed { i, child -> println("[WATCHDOG]   child[$i] active=${child.isActive} completed=${child.isCompleted} cancelled=${child.isCancelled}") }
            fail("Test run timeout reached")
        }

        blockJob = launch {
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
            delay(timeout * watchdogScale)
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

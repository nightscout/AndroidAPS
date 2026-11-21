package app.aaps.pump.aaruy.emulator

import app.aaps.core.interfaces.pump.emulator.EmulatedCommand
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The frame numbers, on the JVM: a number skipped in the client is set by reflection, and the writes come from real
 * threads. The rest of the client against the emulator is `ClientOnEmulatorTest`, which runs on every platform.
 */
class ClientSequenceOnEmulatorTest : ClientOnEmulatorHarness() {

    /** The driver writes from the pump thread and from coroutines. A frame sent out of order is refused by the pump. */
    @Test
    fun writesFromManyThreadsKeepTheSequenceInOrder() {
        val start = CountDownLatch(1)
        val threads = List(8) {
            thread {
                start.await()
                // Each write waits for a drain of the client, so many more than this do not end in the time of waitFor.
                repeat(10) { client.getCurStatus() }
            }
        }
        start.countDown()
        waitFor("all writes queued") { threads.none { it.isAlive } }
        var seen = -1
        waitFor("all frames sent") {
            val now = peripheral.writes.size
            val settled = now == seen
            seen = now
            repeat(10) { drain() }
            settled
        }
        assertEquals(0, emulator.rejectedFrames, "no sequence errors")
    }

    @Test
    fun sequenceErrorRecoversThroughSerialRead() {
        val link = client.javaClass.getDeclaredField("link").apply { isAccessible = true }.get(client)
        link.javaClass.getDeclaredField("sequence").apply { isAccessible = true }.set(link, 500.toShort())
        log.clear()
        client.getCurStatus()
        waitFor("error seen") { logged("onReceiveErrorData(28, 128") }
        assertTrue(emulator.rejectedFrames > 0)
        waitFor("recovered") {
            log.clear()
            client.getCurStatus()
            drain()
            drain()
            drain()
            logged("onReceiveCurrentStatus")
        }
    }

    /**
     * The setting and the start of a bolus are refused for their number and go out again. The pump answers the count read
     * twice: if the second answer were taken too, it would come after the pump took the frames sent again, and they would
     * go out a third time.
     */
    @Test
    fun aBolusRefusedForItsNumberIsGivenOnce() {
        skipANumber()
        log.clear()
        client.setupAndStartBolus(0.15, 0.0, 0, false)
        waitFor("bolus done") { emulator.boluses.isNotEmpty() && emulator.runningStatus == AaruyPumpEmulator.STATUS_BASAL }
        waitWhile(1500, 3.seconds)
        assertTrue(emulator.rejectedFrames > 0, "the setting was refused")
        assertEquals(listOf(0.15), emulator.boluses)
    }

    /** The driver asked about the start before the refusal was known, and was told it went out. It must not go out later. */
    @Test
    fun aStartTheDriverAskedAboutIsNotSentAgain() {
        skipANumber()
        log.clear()
        client.setupAndStartBolus(0.15, 0.0, 0, false)
        waitFor("start sent") { sent(EmulatedCommand.BOLUS_START) }
        assertTrue(client.abandonBolusStart(), "the start went to the radio")
        waitFor("refusal seen") { logged("onReceiveErrorData(28, 128") }
        waitWhile(1500, 3.seconds)
        assertTrue(emulator.boluses.isEmpty(), emulator.boluses.toString())
        assertFalse(emulator.bolusRunning)
    }

    /**
     * The driver gave up on a temp basal before the refusal of its frame was known, and reported a failure. The frame
     * must not go out again: it once ran on the pump 0.4 s after the driver had given up.
     */
    @Test
    fun aTempBasalTheDriverGaveUpOnIsNotSentAgain() {
        client.setClosedMode(1)
        waitFor("closed mode") { emulator.closedMode == 1 }
        skipANumber()
        log.clear()
        client.startTempBasal(1.5, 30)
        waitFor("temp basal sent") { sent(EmulatedCommand.TEMP_BASAL_START) }
        client.abandonTempBasal()
        waitFor("refusal seen") { logged("onReceiveErrorData(28, 128") }
        waitWhile(1500, 3.seconds)
        assertEquals(AaruyPumpEmulator.STATUS_BASAL, emulator.runningStatus, "no temp basal runs")
    }

    private fun sent(command: EmulatedCommand): Boolean =
        peripheral.writes.any { frame -> emulator.peek(frame)?.let { (param, action) -> emulator.isCommand(command, param, action) } == true }

    /** As when a frame is lost on its way: the next frame has a number one too high. */
    private fun skipANumber() {
        val link = client.javaClass.getDeclaredField("link").apply { isAccessible = true }.get(client)
        val sequence = link.javaClass.getDeclaredField("sequence").apply { isAccessible = true }
        sequence.set(link, ((sequence.get(link) as Short) + 1).toShort())
    }
}

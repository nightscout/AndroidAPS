package app.aaps.plugins.eversense.connection

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EversenseConnectionControllerTest {
    private class Clock : EversenseConnectionController.Scheduler {
        data class Entry(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        val queue = mutableListOf<Entry>()
        override fun after(delayMs: Long, action: () -> Unit): EversenseConnectionController.Task {
            val entry = Entry(now + delayMs, action)
            queue.add(entry)
            return EversenseConnectionController.Task { entry.cancelled = true }
        }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val next = queue.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                queue.remove(next)
                now = next.at
                next.action()
            }
            now = end
        }
        fun pending() = queue.count { !it.cancelled }
    }
    private class Link : EversenseConnectionController.Transport {
        val opened = mutableListOf<Long>()
        var reused = 0
        var closed = 0
        var accepted = true
        override fun open(session: Long): Boolean { opened.add(session); return accepted }
        override fun reuse(session: Long): Boolean { reused++; return accepted }
        override fun close() { closed++ }
    }
    private val clock = Clock()
    private val link = Link()
    private val controller = EversenseConnectionController(clock, { clock.now }, link)
    private fun token() = link.opened.last()

    @Test fun stopCancelsAllTimersAndIgnoresLateCallbacksAndBluetooth() {
        controller.start()
        val old = token()
        val lateDeadline = clock.queue.last().action
        controller.stop()
        controller.ready(old)
        controller.disconnected(old, 19, true)
        controller.bluetoothAvailable()
        lateDeadline()
        clock.advance(1_000_000)
        assertEquals(EversenseConnectionController.State.STOPPED, controller.state)
        assertEquals(1, link.opened.size)
        assertEquals(0, clock.pending())
        assertFalse(controller.connect())
    }
    @Test fun manualDisconnectRemainsPausedUntilExplicitConnect() {
        controller.start()
        controller.disconnect()
        controller.bluetoothAvailable()
        clock.advance(1_000_000)
        assertEquals(1, link.opened.size)
        assertEquals(EversenseConnectionController.State.PAUSED, controller.state)
        assertTrue(controller.connect())
        assertEquals(2, link.opened.size)
    }
    @Test fun duplicateDisconnectsCreateOneRetryAndOldSessionCannotAffectNewOne() {
        controller.start()
        val old = token()
        controller.disconnected(old, 133)
        controller.disconnected(old, 133)
        assertEquals(1, clock.pending())
        clock.advance(4_999)
        assertEquals(1, link.opened.size)
        clock.advance(1)
        assertEquals(2, link.opened.size)
        controller.ready(old)
        controller.disconnected(old, 133)
        assertEquals(EversenseConnectionController.State.CONNECTING, controller.state)
        assertEquals(1, clock.pending())
    }
    @Test fun repeatedFailuresBackOffAndSuccessfulAuthenticationResetsBackoff() {
        controller.start()
        for (delay in listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)) {
            controller.disconnected(token(), 133)
            val before = link.opened.size
            clock.advance(delay - 1)
            assertEquals(before, link.opened.size)
            clock.advance(1)
            assertEquals(before + 1, link.opened.size)
        }
        controller.ready(token())
        controller.disconnected(token(), 133)
        val before = link.opened.size
        clock.advance(5_000)
        assertEquals(before + 1, link.opened.size)
    }
    @Test fun normal365DisconnectionReusesGattInsteadOfOpeningAnotherClient() {
        controller.start()
        controller.ready(token())
        val closed = link.closed
        controller.disconnected(token(), 19, reuse = true)
        clock.advance(5_000)
        assertEquals(1, link.reused)
        assertEquals(1, link.opened.size)
        assertEquals(closed, link.closed)
        controller.ready(token())
        assertEquals(EversenseConnectionController.State.READY, controller.state)
    }
    @Test fun e3Status19WaitsThirtySeconds() {
        controller.start()
        controller.disconnected(token(), 19)
        clock.advance(29_999)
        assertEquals(1, link.opened.size)
        clock.advance(1)
        assertEquals(2, link.opened.size)
    }
    @Test fun stalledAttemptTimesOutAndDoesNotRemainConnectingForever() {
        controller.start()
        controller.connect()
        assertEquals(1, link.opened.size)
        clock.advance(180_000)
        assertEquals(EversenseConnectionController.State.DISCONNECTED, controller.state)
        clock.advance(5_000)
        assertEquals(2, link.opened.size)
    }
    @Test fun watchdogReplacesOnlyReadyConnectionsWithoutRecentData() {
        controller.start()
        controller.ready(token())
        clock.advance(6 * 60_000)
        controller.dataReceived(token())
        clock.advance(7 * 60_000)
        assertEquals(1, link.opened.size)
        clock.advance(60_000)
        assertEquals(2, link.opened.size)
    }
    @Test fun rejectedConnectHasOneBoundedRetry() {
        link.accepted = false
        controller.start()
        assertEquals(1, clock.pending())
        clock.advance(59_999)
        assertEquals(1, link.opened.size)
        clock.advance(1)
        assertEquals(2, link.opened.size)
        assertEquals(1, clock.pending())
    }
    @Test fun startWithoutCredentialsDoesNotOpenBluetooth() {
        controller.start(autoConnect = false)
        controller.bluetoothAvailable()
        clock.advance(1_000_000)
        assertEquals(0, link.opened.size)
        assertTrue(controller.connect())
    }
}

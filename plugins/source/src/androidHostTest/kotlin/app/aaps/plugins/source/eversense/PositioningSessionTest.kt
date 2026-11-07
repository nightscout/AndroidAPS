package app.aaps.plugins.source.eversense

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PositioningSessionTest {
    @Test fun leavingBeforeDelayedStartDoesNotEnterDiagnosticMode() = runTest {
        val events = mutableListOf<String>()
        val session = PositioningSession(backgroundScope, { events.add("enter") }, { events.add("read") }, { events.add("exit") })
        session.resume()
        runCurrent()
        advanceTimeBy(499)
        session.pause()
        runCurrent()
        advanceTimeBy(10_000)
        assertTrue(events.isEmpty())
    }
    @Test fun pauseStopsPollingAndExitsDiagnosticMode() = runTest {
        val events = mutableListOf<String>()
        val session = PositioningSession(backgroundScope, { events.add("enter") }, { events.add("read") }, { events.add("exit") })
        session.resume()
        advanceTimeBy(501)
        assertEquals(listOf("enter", "read"), events)
        session.pause()
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(listOf("enter", "read", "exit"), events)
    }
    @Test fun rapidResumeWaitsForPreviousExitBeforeEnteringAgain() = runTest {
        val events = mutableListOf<String>()
        val session = PositioningSession(backgroundScope,
            { events.add("enter") }, { events.add("read") },
            { events.add("exit-start"); delay(1_000); events.add("exit-end") })
        session.resume()
        advanceTimeBy(501)
        session.pause()
        session.resume()
        advanceTimeBy(1_001)
        assertEquals(listOf("enter", "read", "exit-start", "exit-end"), events)
        advanceTimeBy(500)
        assertEquals("enter", events[4])
        session.pause()
        advanceTimeBy(1_001)
    }
    @Test fun cancellationDuringEntryStillExitsDiagnosticMode() = runTest {
        val entered = CompletableDeferred<Unit>()
        var exits = 0
        val session = PositioningSession(backgroundScope, { entered.complete(Unit); delay(10_000) }, {}, { exits++ })
        session.resume()
        advanceTimeBy(501)
        assertTrue(entered.isCompleted)
        session.pause()
        runCurrent()
        assertEquals(1, exits)
    }
}

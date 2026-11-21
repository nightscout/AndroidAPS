package app.aaps.core.interfaces.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The test scheduler's time stands for the time the device is awake, which is what a coroutine delay
 * counts. [SleepingClock] is the wall clock: it moves with the scheduler, plus whatever the phone slept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WallClockDelayTest {

    private class SleepingClock(private val scope: TestScope) : Clock {

        var sleptMs = 0L

        override fun now(): Instant = Instant.fromEpochMilliseconds(START + scope.testScheduler.currentTime + sleptMs)
    }

    @Test
    fun `ends at the target when the phone never sleeps`() = runTest {
        val clock = SleepingClock(this)
        var done = false
        launch { clock.delayUntil(START + 10.minutes.inWholeMilliseconds); done = true }

        advanceTimeBy(10.minutes.inWholeMilliseconds - 1)
        runCurrent()
        assertFalse(done, "one millisecond early is still early")

        advanceTimeBy(1)
        runCurrent()
        assertTrue(done)
    }

    /** The bug it fixes: one long delay would still wait the full 30 minutes of awake time. */
    @Test
    fun `after the phone slept it ends within one step of the real time`() = runTest {
        val clock = SleepingClock(this)
        var done = false
        launch { clock.delayUntil(START + 30.minutes.inWholeMilliseconds, step = 30.seconds) }.invokeOnCompletion { done = true }

        advanceTimeBy(5.minutes.inWholeMilliseconds)
        runCurrent()
        clock.sleptMs = 1.minutes.inWholeMilliseconds * 60 // the phone slept for an hour

        advanceTimeBy(30.seconds.inWholeMilliseconds)
        runCurrent()
        assertTrue(done, "the real time is past the end, so one step later it is over")
    }

    @Test
    fun `a target in the past returns at once`() = runTest {
        val clock = SleepingClock(this)
        var done = false
        launch { clock.delayUntil(START - 1); done = true }

        runCurrent()
        assertTrue(done)
    }

    private companion object {

        const val START = 1_790_000_000_000L
    }
}

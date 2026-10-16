package app.aaps.core.objects.extensions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtensionKtTest {

    // -- tickerFlow ------------------------------------------------------------------------

    @Test
    fun tickerFlow_emitsImmediatelyOnSubscribe() = runTest {
        // first() returns synchronously without advancing virtual time — proves the first
        // emission arrives before any delay. freshness() and any "evaluate on subscribe"
        // caller depends on this.
        val first = tickerFlow(periodMs = 60_000L).first()
        assertThat(first).isEqualTo(Unit)
    }

    @Test
    fun tickerFlow_emitsOncePerPeriod() = runTest {
        // Collect 4 emissions on a 1s ticker — at virtual t=0, 1000, 2000, 3000.
        val emissionTimes = mutableListOf<Long>()
        val job = launch {
            tickerFlow(periodMs = 1_000L).take(4).collect {
                emissionTimes.add(currentTime)
            }
        }
        advanceTimeBy(3_500L)
        runCurrent()
        job.join()
        // Period applies after the first immediate emit, so cadence is t=0, 1s, 2s, 3s.
        assertThat(emissionTimes).containsExactly(0L, 1_000L, 2_000L, 3_000L).inOrder()
    }

    // -- freshness -------------------------------------------------------------------------
    //
    // Two important rules for these tests:
    //
    // 1. `freshness` runs in `backgroundScope` (not the test scope). runTest fails the test if
    //    coroutines from its main scope are still alive when the body returns — WhileSubscribed
    //    has a 5s shutdown delay so the upstream lingers past any job.cancel(). backgroundScope
    //    is the test-framework's escape hatch: jobs there are auto-cancelled at teardown.
    //
    // 2. Use `runCurrent()`, NOT `advanceUntilIdle()`. The ticker's `while(true) { delay() }`
    //    is never "idle" — advanceUntilIdle would loop forever ticking virtual time. runCurrent
    //    just drains immediately-runnable coroutines, which is exactly what we need to let the
    //    stateIn upstream collect the first combine emission.

    @Test
    fun freshness_pristineTrueWhenTimestampZero() = runTest {
        val ts = MutableStateFlow(0L)
        val fresh = ts.freshness(
            thresholdMs = 10_000L,
            scope = backgroundScope,
            tickMs = 60_000L,
            pristine = true,
            now = { 1_700_000_000_000L }
        )
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { fresh.collect { collected.add(it) } }
        runCurrent()
        // pristine = true → 0L is treated as fresh (don't lock pre-first-heartbeat).
        assertThat(collected.last()).isTrue()
    }

    @Test
    fun freshness_pristineFalseLocksWhenTimestampZero() = runTest {
        val ts = MutableStateFlow(0L)
        val fresh = ts.freshness(
            thresholdMs = 10_000L,
            scope = backgroundScope,
            tickMs = 60_000L,
            pristine = false,
            now = { 1_700_000_000_000L }
        )
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { fresh.collect { collected.add(it) } }
        runCurrent()
        // pristine = false → fail-closed, refuse to evaluate before first heartbeat.
        assertThat(collected.last()).isFalse()
    }

    @Test
    fun freshness_trueWhenTimestampWithinThreshold() = runTest {
        val ts = MutableStateFlow(1_700_000_000_000L)
        // now is 5s after ts; threshold is 10s → fresh.
        val fresh = ts.freshness(
            thresholdMs = 10_000L,
            scope = backgroundScope,
            tickMs = 60_000L,
            now = { 1_700_000_005_000L }
        )
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { fresh.collect { collected.add(it) } }
        runCurrent()
        assertThat(collected.last()).isTrue()
    }

    @Test
    fun freshness_falseWhenTimestampBeyondThreshold() = runTest {
        val ts = MutableStateFlow(1_700_000_000_000L)
        // now is 15s after ts; threshold is 10s → stale.
        val fresh = ts.freshness(
            thresholdMs = 10_000L,
            scope = backgroundScope,
            tickMs = 60_000L,
            now = { 1_700_000_015_000L }
        )
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { fresh.collect { collected.add(it) } }
        runCurrent()
        assertThat(collected.last()).isFalse()
    }

    @Test
    fun freshness_tickerReEvaluatesWhenWallClockMovesPastThreshold() = runTest {
        // Wall-clock progression alone — without a timestamp update — must flip freshness
        // false once the threshold is crossed. The internal ticker drives re-evaluation.
        val ts = MutableStateFlow(1_700_000_000_000L)
        var fakeNow = 1_700_000_000_000L
        val fresh = ts.freshness(
            thresholdMs = 10_000L,
            scope = backgroundScope,
            tickMs = 1_000L,
            now = { fakeNow }
        )
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { fresh.collect { collected.add(it) } }
        runCurrent()
        assertThat(collected.last()).isTrue()
        // Move wall-clock past threshold and let the ticker fire once.
        fakeNow = 1_700_000_011_000L
        advanceTimeBy(1_200L)
        runCurrent()
        assertThat(collected.last()).isFalse()
    }

    @Test
    fun freshness_timestampBumpFlipsTrueWhenStale() = runTest {
        // A new heartbeat must immediately unlock without waiting for the next ticker
        // emission — the StateFlow<Long> emission drives the combine directly.
        val ts = MutableStateFlow(1_700_000_000_000L)
        val fakeNow = 1_700_000_020_000L          // 20s past initial ts → stale
        val fresh = ts.freshness(
            thresholdMs = 10_000L,
            scope = backgroundScope,
            tickMs = 60_000L,
            now = { fakeNow }
        )
        val collected = mutableListOf<Boolean>()
        backgroundScope.launch { fresh.collect { collected.add(it) } }
        runCurrent()
        assertThat(collected.last()).isFalse()
        // Fresh heartbeat arrives (within threshold of current fakeNow).
        ts.value = fakeNow - 1_000L
        runCurrent()
        assertThat(collected.last()).isTrue()
    }
}

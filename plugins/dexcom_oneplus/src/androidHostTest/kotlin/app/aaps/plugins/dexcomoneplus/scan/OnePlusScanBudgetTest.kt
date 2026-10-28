package app.aaps.plugins.dexcomoneplus.scan

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OnePlusScanBudgetTest {

    @BeforeEach
    fun reset() {
        OnePlusScanBudget.reset()
    }

    @Test
    fun `starts below the reserve are never delayed`() {
        repeat(OnePlusScanBudget.MAX_STARTS_PER_WINDOW - 1) { OnePlusScanBudget.record(1_000L) }

        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(0L)
    }

    @Test
    fun `a full window defers the next start until the oldest start ages out`() {
        repeat(OnePlusScanBudget.MAX_STARTS_PER_WINDOW) { OnePlusScanBudget.record(1_000L + it) }

        // Oldest start is at 1_000; the window frees at 1_000 + WINDOW_MS.
        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(OnePlusScanBudget.WINDOW_MS)
        assertThat(OnePlusScanBudget.waitMsFor(11_000L)).isEqualTo(OnePlusScanBudget.WINDOW_MS - 10_000L)
        assertThat(OnePlusScanBudget.waitMsFor(1_000L + OnePlusScanBudget.WINDOW_MS)).isEqualTo(0L)
    }

    @Test
    fun `budget keeps one platform slot free for the UI discovery scan`() {
        assertThat(OnePlusScanBudget.MAX_STARTS_PER_WINDOW)
            .isLessThan(OnePlusScanBudget.MAX_STARTS_PER_WINDOW_PLATFORM)
    }

    @Test
    fun `two slots waiting concurrently stay inside the platform quota`() {
        // Both slots restart a bounded 8 s wait every ~8.25 s. Without the shared budget that is
        // ~15 starts per minute — over the platform throttle, which blinds both silently.
        var now = 0L
        var starts = 0
        val cadenceMs = 8_250L / 2 // two slots interleaved
        while (now < 60_000L) {
            val wait = OnePlusScanBudget.reserve(now)
            if (wait > 0L) {
                now += wait
                continue
            }
            starts++
            now += cadenceMs
        }

        // Any 30 s window must never exceed what we allow ourselves.
        assertThat(OnePlusScanBudget.startsInWindow(60_000L))
            .isAtMost(OnePlusScanBudget.MAX_STARTS_PER_WINDOW)
        assertThat(starts).isAtMost(2 * OnePlusScanBudget.MAX_STARTS_PER_WINDOW + 1)
    }

    @Test
    fun `reserve books the slot atomically so two racing slots cannot share it`() {
        repeat(OnePlusScanBudget.MAX_STARTS_PER_WINDOW - 1) { OnePlusScanBudget.reserve(1_000L) }

        // Last free slot goes to the first caller; the racing one is deferred.
        assertThat(OnePlusScanBudget.reserve(1_000L)).isEqualTo(0L)
        assertThat(OnePlusScanBudget.reserve(1_000L)).isGreaterThan(0L)
        assertThat(OnePlusScanBudget.startsInWindow(1_000L))
            .isEqualTo(OnePlusScanBudget.MAX_STARTS_PER_WINDOW)
    }

    @Test
    fun `an explicit block holds back starts even when the quota is free`() {
        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(0L)

        OnePlusScanBudget.blockFor(nowMs = 1_000L, durationMs = 5_000L)

        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(5_000L)
        assertThat(OnePlusScanBudget.waitMsFor(3_000L)).isEqualTo(3_000L)
        assertThat(OnePlusScanBudget.waitMsFor(6_000L)).isEqualTo(0L)
    }

    @Test
    fun `a longer block wins and a shorter one cannot shorten it`() {
        OnePlusScanBudget.blockFor(nowMs = 1_000L, durationMs = 10_000L)
        OnePlusScanBudget.blockFor(nowMs = 1_000L, durationMs = 2_000L)

        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(10_000L)
    }

    @Test
    fun `reserve does not book a slot while blocked`() {
        OnePlusScanBudget.blockFor(nowMs = 1_000L, durationMs = 5_000L)

        assertThat(OnePlusScanBudget.reserve(1_000L)).isGreaterThan(0L)
        assertThat(OnePlusScanBudget.startsInWindow(1_000L)).isEqualTo(0)
    }

    @Test
    fun `reset clears an active block`() {
        OnePlusScanBudget.blockFor(nowMs = 1_000L, durationMs = 30_000L)

        OnePlusScanBudget.reset()

        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(0L)
    }

    @Test
    fun `a negative block duration is treated as no block`() {
        OnePlusScanBudget.blockFor(nowMs = 1_000L, durationMs = -5_000L)

        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(0L)
    }

    @Test
    fun `a backwards clock jump cannot wedge the budget`() {
        repeat(OnePlusScanBudget.MAX_STARTS_PER_WINDOW) { OnePlusScanBudget.record(500_000L) }

        assertThat(OnePlusScanBudget.waitMsFor(1_000L)).isEqualTo(0L)
    }
}

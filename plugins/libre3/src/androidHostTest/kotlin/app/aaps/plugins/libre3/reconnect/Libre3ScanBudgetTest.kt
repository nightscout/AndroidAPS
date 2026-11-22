package app.aaps.plugins.libre3.reconnect

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** How many scans the two slots may start together before the platform stops answering. */
class Libre3ScanBudgetTest {

    private val start = 1_000_000L

    @BeforeEach
    fun clearBudget() {
        Libre3ScanBudget.reset()
    }

    @Test
    fun `the allowed starts fit in one window and the next one does not`() {
        for (i in 0 until Libre3ScanBudget.MAX_SCAN_STARTS) {
            assertThat(Libre3ScanBudget.tryAcquire(start + i)).isTrue()
        }

        assertThat(Libre3ScanBudget.tryAcquire(start + Libre3ScanBudget.MAX_SCAN_STARTS)).isFalse()
    }

    @Test
    fun `a start is allowed again once the window has rolled past`() {
        for (i in 0 until Libre3ScanBudget.MAX_SCAN_STARTS) {
            Libre3ScanBudget.tryAcquire(start + i)
        }

        assertThat(Libre3ScanBudget.tryAcquire(start + Libre3ScanBudget.WINDOW_MS)).isTrue()
    }

    @Test
    fun `there is no wait while the budget has room`() {
        Libre3ScanBudget.tryAcquire(start)

        assertThat(Libre3ScanBudget.waitMsUntilNextStart(start)).isEqualTo(0L)
    }

    @Test
    fun `a full budget waits exactly the rest of the window`() {
        for (i in 0 until Libre3ScanBudget.MAX_SCAN_STARTS) {
            Libre3ScanBudget.tryAcquire(start + i)
        }

        // The oldest start is at `start`, so the window is free again a full window after it.
        assertThat(Libre3ScanBudget.waitMsUntilNextStart(start + 5_000L))
            .isEqualTo(Libre3ScanBudget.WINDOW_MS - 5_000L)
    }

    @Test
    fun `a refused start does not count against the window`() {
        for (i in 0 until Libre3ScanBudget.MAX_SCAN_STARTS) {
            Libre3ScanBudget.tryAcquire(start + i)
        }
        Libre3ScanBudget.tryAcquire(start + 10_000L)

        assertThat(Libre3ScanBudget.startsInWindow(start + 10_000L))
            .isEqualTo(Libre3ScanBudget.MAX_SCAN_STARTS)
    }

    @Test
    fun `a clock that jumps backwards does not wedge the budget`() {
        for (i in 0 until Libre3ScanBudget.MAX_SCAN_STARTS) {
            Libre3ScanBudget.tryAcquire(start + i)
        }

        assertThat(Libre3ScanBudget.tryAcquire(start - Libre3ScanBudget.WINDOW_MS)).isTrue()
    }
}

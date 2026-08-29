package app.aaps.workflow

import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.HISTORY_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.UPDATE_PREDICTIONS
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Pins the rule the class exists for: a slot always holds the generation the work was tagged with.
 *
 * A worker asks for its input with the generation it was enqueued under, and gets null when the
 * chain has moved on. If the counter bump and the slot write ever came apart, a slot could end up
 * holding an older generation than the work running against it, and the calculation would be
 * dropped silently - no crash, just a graph that never refreshes.
 *
 * The class used to guard that pair with `@Synchronized`, which is JVM only. It is a compare-and-set
 * loop now so it also compiles for iOS, and these tests are what say the swap kept the rule.
 */
class WorkflowChainDataTest : TestBase() {

    private fun prepare() = PrepareGraphData(
        iobCobCalculator = mock(),
        overviewData = mock(),
        cache = mock(),
        signals = mock(),
        reason = "test",
        end = 0L,
        bgDataReload = false,
        limitDataToOldestAvailable = false,
        triggeredByNewBG = false,
        emitFinalProgress = false
    )

    private fun post() = PostCalculationData(
        overviewData = mock(),
        cache = mock(),
        signals = mock(),
        triggeredByNewBG = false,
        runLoopAndWidgetPhase = false
    )

    private fun newSut() = WorkflowChainData(aapsLogger)

    @Test fun `the generation a start returns is the one the slot answers to`() {
        val sut = newSut()
        val gen = sut.startMain(prepare(), post())

        assertThat(sut.prepareFor(MAIN_CALCULATION, gen)).isNotNull()
        assertThat(sut.postFor(MAIN_CALCULATION, gen)).isNotNull()
    }

    @Test fun `a superseded generation gets nothing`() {
        val sut = newSut()
        val first = sut.startMain(prepare(), post())
        val second = sut.startMain(prepare(), post())

        assertThat(second).isGreaterThan(first)
        // The tail of the first chain must not run against the second chain's data.
        assertThat(sut.prepareFor(MAIN_CALCULATION, first)).isNull()
        assertThat(sut.prepareFor(MAIN_CALCULATION, second)).isNotNull()
    }

    @Test fun `the three slots are independent`() {
        val sut = newSut()
        val main = sut.startMain(prepare(), post())
        val history = sut.startHistory(prepare())
        val predictions = sut.startPredictions(post())

        // Starting one slot must not invalidate another, even though the counter is shared.
        assertThat(sut.prepareFor(MAIN_CALCULATION, main)).isNotNull()
        assertThat(sut.prepareFor(HISTORY_CALCULATION, history)).isNotNull()
        assertThat(sut.postFor(UPDATE_PREDICTIONS, predictions)).isNotNull()
    }

    @Test fun `history has no post phase and an unknown job key is refused`() {
        val sut = newSut()
        val history = sut.startHistory(prepare())

        assertThat(sut.postFor(HISTORY_CALCULATION, history)).isNull()
        assertThat(sut.prepareFor("not-a-job", history)).isNull()
        assertThat(sut.postFor(null, history)).isNull()
    }

    @Test fun `nothing started means nothing to run`() {
        val sut = newSut()

        assertThat(sut.prepareFor(MAIN_CALCULATION, 1L)).isNull()
        assertThat(sut.postFor(UPDATE_PREDICTIONS, 1L)).isNull()
    }

    // Real threads on purpose. `runTest` runs coroutines on one virtual thread, so `async` there
    // never actually interleaves and this test would pass even with the atomicity removed - checked
    // by deleting the compare-and-set and watching it still go green.
    @Test fun `concurrent starts never hand out the same generation twice`() = runBlocking(Dispatchers.Default) {
        val sut = newSut()

        val generations = (1..2000).map {
            async(Dispatchers.Default) { sut.startMain(prepare(), post()) }
        }.awaitAll()

        // A lost update would show up as two callers being told they own the same generation, and
        // then as a slot answering to a generation whose work has already been superseded.
        assertThat(generations.toSet()).hasSize(generations.size)
        val winner = generations.max()
        assertThat(sut.prepareFor(MAIN_CALCULATION, winner)).isNotNull()
    }
}

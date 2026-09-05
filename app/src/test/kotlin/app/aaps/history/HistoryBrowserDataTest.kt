package app.aaps.history

import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.di.metro.HistoryWindowGraph
import app.aaps.di.metro.testRoot
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import app.aaps.ui.compose.overview.OverviewDataCacheImpl
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The History Browser must not share calculation objects with the running loop.
 * These assertions used to run against `HistoryBrowserData`, which built the objects by hand. The
 * objects now come from [HistoryWindowGraph], so the tests build the graph instead - the same
 * guarantees, checked one layer closer to where they are now decided.
 */
class HistoryBrowserDataTest : TestBaseWithProfile() {

    @Mock lateinit var calculationWorkflow: CalculationWorkflow

    private lateinit var sut: HistoryWindowGraph

    @BeforeEach
    fun setUp() {
        sut = createSut()
    }

    /**
     * The window's cache, as the concrete type.
     */
    private val HistoryWindowGraph.cacheImpl: OverviewDataCacheImpl get() = cache as OverviewDataCacheImpl

    /**
     * Builds the real root and opens windows from it, rather than building a window directly. That is
     * the point of the check now: the root binds the app-wide IobCobCalculator, the window binds its
     * own, and the window must win. If the extension quietly resolved the parent's calculator instead,
     * history browsing would compute into the running loop's state.
     * Only the leaves this test really needs are stubbed - see [testRoot].
     */
    // No leaf stub for ResourceHelper any more: Metro owns it, and the Android work that made building
    // it here unsafe moved out of the constructor into `start()`, which only the Application calls.
    private fun root() = testRoot()

    private fun createSut(): HistoryWindowGraph = root().historyWindowFactory.create()

    @Test
    fun `builds its own overview data and signals (not the injected singletons)`() {
        assertThat(sut.overviewData).isInstanceOf(OverviewDataImpl::class.java)
        assertThat(sut.signals).isInstanceOf(CalculationSignalsImpl::class.java)
    }

    /**
     * Distinct objects, not merely objects of the right class.
     *
     * The test above asserts the types, which would still pass if the window resolved the parent`s
     * singletons - `OverviewDataImpl` is `@ContributesBinding` + `@SingleIn` in the root, so the
     * window`s four-line provider only shadows it. Delete that provider and everything still
     * compiles; history browsing then recalculates into the state the running loop is dosing from.
     * The cache already had this assertion. Overview data and signals did not.
     */
    @Test
    fun `the window overview data and signals are its own not the app-wide ones`() {
        val shared = root()
        val window = shared.historyWindowFactory.create()

        assertThat(window.overviewData).isNotSameInstanceAs(shared.overviewData)
        assertThat(window.signals).isNotSameInstanceAs(shared.calculationSignalsEmitter)
    }

    @Test
    fun `builds its own IobCobCalculator instance`() {
        assertThat(sut.iobCobCalculator).isInstanceOf(IobCobCalculatorPlugin::class.java)
    }

    @Test
    fun `creates the cache for history (no DB observation) wired to its own signals`() {
        // observeDatabase = false is the whole point of a history window: the cache must be filled by
        // the window's workers through its own signals, never by the live database stream.
        assertThat(sut.cacheImpl.observeDatabase).isFalse()
        assertThat(sut.cacheImpl.signals).isSameInstanceAs(sut.signals)
    }

    @Test
    fun `the window's cache is its own, not the app-wide one`() {
        val shared = root()
        val window = shared.historyWindowFactory.create()

        assertThat(window.cache).isNotSameInstanceAs(shared.overviewDataCache)
        assertThat(shared.historyWindowFactory.create().cache).isNotSameInstanceAs(window.cache)
    }

    @Test
    fun `the cache's IobCobCalculator provider resolves to the scope's calculator`() {
        // The deferred side of the cycle: the cache holds a provider, not a calculator, and resolving it
        // must land on the window's own calculator rather than the root's.
        assertThat(sut.cacheImpl.iobCobCalculatorProvider.invoke()).isSameInstanceAs(sut.iobCobCalculator)
    }

    @Test
    fun `objects are the same within one window`() {
        assertThat(sut.overviewData).isSameInstanceAs(sut.overviewData)
        assertThat(sut.iobCobCalculator).isSameInstanceAs(sut.iobCobCalculator)
    }

    @Test
    fun `two windows of the same root own separate overview data and calculator`() {
        // One root, two windows - not two roots. Two roots share nothing anyway, so that would prove
        // nothing about the extension scope. This is the real question: does opening a second window
        // give it its own calculation objects while both still sit under one application graph.
        val shared = root()
        val first = shared.historyWindowFactory.create()
        val second = shared.historyWindowFactory.create()

        assertThat(second.overviewData).isNotSameInstanceAs(first.overviewData)
        assertThat(second.iobCobCalculator).isNotSameInstanceAs(first.iobCobCalculator)
        assertThat(second.signals).isNotSameInstanceAs(first.signals)
    }

    @Test
    fun `the window does not fall through to the application's own calculator`() {
        // The root's IobCobCalculator is deliberately a handle that throws. Reaching this line at all
        // means the window resolved its own binding and never touched the parent's - which is the
        // whole safety property: history recalculating must not run on the loop's calculator.
        assertThat(sut.iobCobCalculator).isInstanceOf(IobCobCalculatorPlugin::class.java)
    }
}

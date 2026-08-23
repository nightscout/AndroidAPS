package app.aaps.history

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.DeferredRef
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.di.metro.HistoryWindowGraph
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import app.aaps.ui.compose.overview.OverviewDataCacheImpl
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.createGraphFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The History Browser must not share calculation objects with the running loop.
 *
 * These assertions used to run against `HistoryBrowserData`, which built the objects by hand. The
 * objects now come from [HistoryWindowGraph], so the tests build the graph instead - the same
 * guarantees, checked one layer closer to where they are now decided.
 */
class HistoryBrowserDataTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var calculationWorkflow: CalculationWorkflow
    @Mock lateinit var overviewDataCacheFactory: OverviewDataCacheFactory

    private lateinit var cache: OverviewDataCacheImpl
    private lateinit var sut: HistoryWindowGraph

    @BeforeEach
    fun setUp() {
        cache = mock()
        whenever(overviewDataCacheFactory.create(any(), any(), any())).thenReturn(cache)
        sut = createSut()
    }

    private fun createSut() = createGraphFactory<HistoryWindowGraph.Factory>().create(
        DeferredRef { aapsLogger },
        DeferredRef { rxBus },
        DeferredRef { preferences },
        DeferredRef { rh },
        DeferredRef { profileFunction },
        DeferredRef { activePlugin },
        DeferredRef { dateUtil },
        DeferredRef { persistenceLayer },
        DeferredRef { calculationWorkflow },
        DeferredRef { decimalFormatter },
        DeferredRef { processedTbrEbData },
        DeferredRef { overviewDataCacheFactory }
    )

    @Test
    fun `builds its own overview data and signals (not the injected singletons)`() {
        assertThat(sut.overviewData).isInstanceOf(OverviewDataImpl::class.java)
        assertThat(sut.signals).isInstanceOf(CalculationSignalsImpl::class.java)
    }

    @Test
    fun `builds its own IobCobCalculator instance`() {
        assertThat(sut.iobCobCalculator).isInstanceOf(IobCobCalculatorPlugin::class.java)
    }

    @Test
    fun `creates the cache for history (no DB observation) wired to its own signals`() {
        val signalsCaptor = argumentCaptor<CalculationSignals>()
        assertThat(sut.cache).isSameInstanceAs(cache)
        verify(overviewDataCacheFactory).create(any(), signalsCaptor.capture(), eq(false))
        assertThat(signalsCaptor.firstValue).isSameInstanceAs(sut.signals)
    }

    @Test
    fun `the cache's IobCobCalculator provider resolves to the scope's calculator`() {
        val providerCaptor = argumentCaptor<() -> IobCobCalculator>()
        // Touch the cache so the factory is called, then check the deferred side of the cycle.
        sut.cache
        verify(overviewDataCacheFactory).create(providerCaptor.capture(), any(), any())
        assertThat(providerCaptor.firstValue.invoke()).isSameInstanceAs(sut.iobCobCalculator)
    }

    @Test
    fun `objects are the same within one window`() {
        assertThat(sut.overviewData).isSameInstanceAs(sut.overviewData)
        assertThat(sut.iobCobCalculator).isSameInstanceAs(sut.iobCobCalculator)
    }

    @Test
    fun `each window owns separate overview data and calculator`() {
        val other = createSut()
        assertThat(other.overviewData).isNotSameInstanceAs(sut.overviewData)
        assertThat(other.iobCobCalculator).isNotSameInstanceAs(sut.iobCobCalculator)
    }
}

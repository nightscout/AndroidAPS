package app.aaps.history

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.implementation.overview.OverviewDataImpl
import app.aaps.plugins.main.iob.iobCobCalculator.IobCobCalculatorPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.ui.compose.overview.OverviewDataCacheFactory
import app.aaps.ui.compose.overview.OverviewDataCacheImpl
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HistoryBrowserDataTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var calculationWorkflow: CalculationWorkflow
    @Mock lateinit var overviewDataCacheFactory: OverviewDataCacheFactory

    private lateinit var cache: OverviewDataCacheImpl
    private lateinit var sut: HistoryBrowserData

    @BeforeEach
    fun setUp() {
        cache = mock()
        whenever(overviewDataCacheFactory.create(any(), any(), any())).thenReturn(cache)
        sut = createSut()
    }

    private fun createSut() = HistoryBrowserData(
        aapsSchedulers, rxBus, aapsLogger, rh, dateUtil, preferences, activePlugin, profileFunction,
        persistenceLayer, fabricPrivacy, calculationWorkflow, decimalFormatter, processedTbrEbData,
        overviewDataCacheFactory
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
        verify(overviewDataCacheFactory).create(any(), signalsCaptor.capture(), eq(false))
        assertThat(sut.cache).isSameInstanceAs(cache)
        assertThat(signalsCaptor.firstValue).isSameInstanceAs(sut.signals)
    }

    @Test
    fun `the cache's IobCobCalculator provider resolves to the scope's calculator`() {
        val providerCaptor = argumentCaptor<() -> IobCobCalculator>()
        verify(overviewDataCacheFactory).create(providerCaptor.capture(), any(), any())
        assertThat(providerCaptor.firstValue.invoke()).isSameInstanceAs(sut.iobCobCalculator)
    }

    @Test
    fun `each instance owns separate overview data and calculator`() {
        val other = createSut()
        assertThat(other.overviewData).isNotSameInstanceAs(sut.overviewData)
        assertThat(other.iobCobCalculator).isNotSameInstanceAs(sut.iobCobCalculator)
    }

    @Test
    fun `onDestroy does not throw`() {
        sut.onDestroy()
    }
}

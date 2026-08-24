package app.aaps.history

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.objects.workflow.CalculationSignalsImpl
import app.aaps.di.metro.AapsLeaves
import app.aaps.di.metro.AppRootGraph
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
import javax.inject.Provider

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

    /**
     * A leaf this test must never need. It throws instead of returning a mock, so that a binding the
     * window graph should not touch cannot pass unnoticed.
     */
    private fun <T : Any> unused(): Provider<T> = Provider { error("must not be resolved") }

    /**
     * The leaves the app-wide graph is given. Named, so adding or reordering a leaf cannot silently
     * shift every argument by one - which the old positional list of thirty-five could.
     */
    private fun leaves() = AapsLeaves(
        aapsLoggerProvider = Provider { aapsLogger },
        receiverStatusStoreProvider = unused(),
        rxBusProvider = Provider { rxBus },
        activePluginProvider = Provider { activePlugin },
        appScopeProvider = unused(),
        fabricPrivacyProvider = unused(),
        runningModeExpiryJobProvider = unused(),
        localAlertUtilsProvider = unused(),
        persistenceLayerProvider = Provider { persistenceLayer },
        configProvider = unused(),
        iobCobCalculatorProvider = unused(),
        loopProvider = unused(),
        dateUtilProvider = Provider { dateUtil },
        profileFunctionProvider = Provider { profileFunction },
        profileUtilProvider = unused(),
        commandQueueProvider = unused(),
        maintenanceProvider = unused(),
        rhProvider = Provider { rh },
        preferencesProvider = Provider { preferences },
        dstHelperProvider = unused(),
        workManagerProvider = unused(),
        concentrationHelperProvider = unused(),
        notificationManagerProvider = unused(),
        activeSceneManagerProvider = unused(),
        sceneExecutorProvider = unused(),
        sceneRepositoryProvider = unused(),
        fileListProviderProvider = unused(),
        storageProvider = unused(),
        userEntryPresentationHelperProvider = unused(),
        dataInboxProvider = unused(),
        cloudStorageManagerProvider = unused(),
        calculationWorkflowProvider = Provider { calculationWorkflow },
        decimalFormatterProvider = Provider { decimalFormatter },
        processedTbrEbDataProvider = Provider { processedTbrEbData },
        overviewDataCacheFactoryProvider = Provider { overviewDataCacheFactory }
    )

    /**
     * Builds the real root and opens windows from it, rather than building a window directly. That is
     * the point of the check now: the root binds the app-wide IobCobCalculator, the window binds its
     * own, and the window must win. If the extension quietly resolved the parent's calculator instead,
     * history browsing would compute into the running loop's state.
     */
    private fun root() = createGraphFactory<AppRootGraph.Factory>().create(leaves())

    private fun createSut(): HistoryWindowGraph = root().historyWindowFactory.create()

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

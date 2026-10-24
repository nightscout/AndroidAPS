package app.aaps.ui.compose.overview

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.workflow.CalculationSignals
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What the running mode chip is given when no running mode is stored.
 *
 * On the master that is not a guess: the loop runs in RM.DEFAULT_MODE then, so the chip must show it.
 * Showing "unknown" there broke the setup wizard E2E test, which opens the running mode menu from a
 * chip that says "loop". On a client nothing is known until a record is synced, so null is correct.
 *
 * runBlocking, not runTest: the cache works on the real IO dispatcher, which virtual time cannot reach.
 */
internal class OverviewDataCacheRunningModeTest {

    private val persistenceLayer: PersistenceLayer = mock()
    private val config: Config = mock()
    private val dateUtil: DateUtil = mock()

    private lateinit var sut: OverviewDataCacheImpl

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(NOW)
        val graphConfigRepository: GraphConfigRepository = mock()
        whenever(graphConfigRepository.graphConfigFlow).thenReturn(MutableStateFlow(GraphConfig()))
        val signals: CalculationSignals = mock()
        whenever(signals.progress).thenReturn(MutableStateFlow(100))
        val iobCobCalculator: IobCobCalculator = mock()
        sut = OverviewDataCacheImpl(
            aapsLogger = mock<AAPSLogger>(),
            persistenceLayer = persistenceLayer,
            processedTbrEbData = mock<ProcessedTbrEbData>(),
            profileUtil = mock<ProfileUtil>(),
            profileFunction = mock<ProfileFunction>(),
            preferences = mock<Preferences>(),
            graphConfigRepository = graphConfigRepository,
            dateUtil = dateUtil,
            trendCalculator = mock<TrendCalculator>(),
            iobCobCalculatorProvider = { iobCobCalculator },
            glucoseStatusProvider = mock<GlucoseStatusProvider>(),
            loop = mock<Loop>(),
            config = config,
            processedDeviceStatusData = mock<ProcessedDeviceStatusData>(),
            rxBus = mock<RxBus>(),
            activePlugin = mock<ActivePlugin>(),
            decimalFormatter = mock<DecimalFormatter>(),
            translator = mock<Translator>(),
            rh = mock<TextResolver>(),
            signals = signals,
            // No database observers: the refresh below is the only reader under test.
            observeDatabase = false
        )
    }

    @Test
    fun `master with nothing stored shows the default mode`() = runBlocking<Unit> {
        whenever(config.AAPSCLIENT).thenReturn(false)
        // What PersistenceLayerImpl.getRunningModeActiveAt answers for an empty table.
        whenever(persistenceLayer.getRunningModeActiveAt(any())).thenReturn(RM(timestamp = 0, mode = RM.DEFAULT_MODE, duration = 0))

        sut.refreshRunningMode()

        val shown = withTimeout(5_000) { sut.runningModeFlow.filterNotNull().first() }
        assertThat(shown.mode).isEqualTo(RM.DEFAULT_MODE)
        verify(persistenceLayer, never()).getRunningModeActiveAtOrNull(any())
    }

    @Test
    fun `master shows the stored mode`() = runBlocking<Unit> {
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(persistenceLayer.getRunningModeActiveAt(any())).thenReturn(RM(timestamp = NOW, mode = RM.Mode.OPEN_LOOP, duration = 0))

        sut.refreshRunningMode()

        assertThat(withTimeout(5_000) { sut.runningModeFlow.filterNotNull().first() }.mode).isEqualTo(RM.Mode.OPEN_LOOP)
    }

    @Test
    fun `client with nothing stored shows nothing rather than a disabled loop`() = runBlocking<Unit> {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(persistenceLayer.getRunningModeActiveAtOrNull(any())).thenReturn(null)

        sut.refreshRunningMode()

        verify(persistenceLayer, timeout(5_000)).getRunningModeActiveAtOrNull(any())
        // The default-filling read must not be used on a client: it would claim "loop disabled".
        verify(persistenceLayer, never()).getRunningModeActiveAt(any())
        assertThat(sut.runningModeFlow.value).isNull()
    }

    @Test
    fun `client shows a synced mode`() = runBlocking<Unit> {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(persistenceLayer.getRunningModeActiveAtOrNull(any())).thenReturn(RM(timestamp = NOW, mode = RM.Mode.CLOSED_LOOP, duration = 0))

        sut.refreshRunningMode()

        assertThat(withTimeout(5_000) { sut.runningModeFlow.filterNotNull().first() }.mode).isEqualTo(RM.Mode.CLOSED_LOOP)
    }

    private companion object {

        const val NOW = 1_000_000_000L
    }
}

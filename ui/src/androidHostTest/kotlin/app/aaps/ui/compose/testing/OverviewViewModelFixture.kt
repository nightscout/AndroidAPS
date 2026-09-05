package app.aaps.ui.compose.testing

import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.GraphConfigRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.ui.compose.manageSheet.FakePumpPlugin
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Builds the four **real** view models the three overview layouts share
 * (`OverviewScreenStacked`, `OverviewScreenSplit`, `OverviewScreenTablet`).
 *
 * All three take the same view model set, so one builder covers all three and the layouts are
 * compared against identical data - which is the only way a difference the test finds can be
 * attributed to the layout rather than to the fixture.
 *
 * Shares [AapsScreenFixture.preferences], `dateUtil` and `profileUtil` with the screen environment
 * so what a view model reads and what the composables read cannot drift apart.
 *
 * The data source is [FakeOverviewDataCache], a real in-memory object rather than a mock: the view
 * models read about twenty flows off it while they are being constructed, and one missing `whenever`
 * lands as an NPE inside a `combine`, nowhere near the gap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OverviewViewModelFixture(private val screen: AapsScreenFixture) {

    val cache = FakeOverviewDataCache()

    val rh: ResourceHelper = mock()
    val aapsLogger: AAPSLogger = mock()
    val rxBus: RxBus = mock()
    val persistenceLayer: PersistenceLayer = mock()
    val activePlugin: ActivePlugin = mock()
    val profileFunction: ProfileFunction = mock()
    val loop: Loop = mock()

    /** Shared with the composables through `LocalDecimalFormatter`, so the two cannot format differently. */
    val decimalFormatter: DecimalFormatter = screen.decimalFormatter
    val processedDeviceStatusData: ProcessedDeviceStatusData = mock()
    val iobCobCalculator: IobCobCalculator = mock()
    val constraintChecker: ConstraintsChecker = mock()
    val graphConfigRepository: GraphConfigRepository = mock()
    val nsClient: NsClient = mock()
    val visibilityContext: VisibilityContext = mock()
    val batchExecutor: BatchExecutor = mock()
    val processedTbrEbData: ProcessedTbrEbData = mock()
    val bgSource: BgSource = mock()
    val tddCalculator: TddCalculator = mock()

    /**
     * `activePump` and `activePumpInternal` are two different types on [ActivePlugin]
     * (`PumpWithConcentration` and `Pump`), and the second one is cast to `PluginBase` at
     * [ManageViewModel] field init - so the fixture needs both shapes, backed by one description.
     */
    val activePump: PumpWithConcentration = mock()
    val pumpPlugin: FakePumpPlugin = mock()
    val pumpDescription = PumpDescription()

    init {
        stubResourcesFromRobolectric(rh)

        // ----- shared ambient -----
        whenever(screen.dateUtil.now()).thenReturn(NOW)
        whenever(screen.dateUtil.timeString()).thenReturn(CLOCK_TIME)
        // Only the BG reading's own timestamp produces an age. Any other timestamp - including the
        // one a mis-wired clock would pass - reads as no age at all, so a test asserting on the
        // clock text fails on the assertion rather than on a missing stub.
        whenever(screen.dateUtil.minAgoShort(anyOrNull())).thenReturn("")
        whenever(screen.dateUtil.minAgoShort(BG_TIMESTAMP)).thenReturn(CLOCK_AGO)
        whenever(screen.dateUtil.minAgo(any(), anyOrNull())).thenReturn(TIME_AGO)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        runBlocking {
            // No running profile: the reservoir has no concentration to convert with, and the
            // sensitivity chip takes its "no variable ISF" branch. Both are the empty-data cases.
            whenever(profileFunction.getProfile()).thenReturn(null)
            whenever(loop.runningMode()).thenReturn(RM.Mode.OPEN_LOOP)
        }

        // ----- GraphViewModel -----
        whenever(screen.preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        whenever(screen.preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(72.0)
        whenever(screen.preferences.observe(UnitDoubleKey.OverviewHighMark)).thenReturn(MutableStateFlow(180.0))
        whenever(screen.preferences.observe(UnitDoubleKey.OverviewLowMark)).thenReturn(MutableStateFlow(72.0))
        whenever(graphConfigRepository.graphConfigFlow).thenReturn(MutableStateFlow(GraphConfig()))

        // ----- ChipsViewModel: the three chip flows evaluate as soon as a composable collects them,
        // and an unstubbed non-null return would fail inside the flow, not at the call site. -----
        runBlocking {
            whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(NOW))
            whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(NOW))
            whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(NOW, null, 0.0))
        }
        whenever(iobCobCalculator.ads).thenReturn(mock<AutosensDataStore>())
        val autosensEnabled: Constraint<Boolean> = mock()
        whenever(autosensEnabled.value()).thenReturn(true)
        whenever(constraintChecker.isAutosensModeEnabled()).thenReturn(autosensEnabled)

        // ----- StatusViewModel / ManageViewModel -----
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePlugin.activePumpInternal).thenReturn(pumpPlugin)
        whenever(activePlugin.activeBgSource).thenReturn(bgSource)
        whenever(bgSource.sensorBatteryLevel).thenReturn(-1)
        whenever(activePump.pumpDescription).thenReturn(pumpDescription)
        whenever(activePump.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        whenever(pumpPlugin.pumpDescription).thenReturn(pumpDescription)
        whenever(pumpPlugin.batteryLevel).thenReturn(MutableStateFlow<Int?>(null))
        whenever(rxBus.toFlow(EventInitializationChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventNsClientStatusUpdated::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventCustomActionsChanged::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TE::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(EB::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(TB::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.databaseClearedFlow).thenReturn(emptyFlow())
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(false))
    }

    val graphViewModel: GraphViewModel by lazy {
        GraphViewModel(cache, false, graphConfigRepository, aapsLogger, screen.preferences, screen.dateUtil, rh)
    }

    val chipsViewModel: ChipsViewModel by lazy {
        ChipsViewModel(
            cache, iobCobCalculator, loop, screen.config, persistenceLayer, constraintChecker, profileFunction,
            processedDeviceStatusData, screen.profileUtil, activePlugin, rh, decimalFormatter, screen.dateUtil,
            aapsLogger, screen.preferences, rxBus
        )
    }

    val statusViewModel: StatusViewModel by lazy {
        StatusViewModel(
            rh, activePlugin, profileFunction, screen.config, persistenceLayer, screen.dateUtil, rxBus,
            screen.preferences, tddCalculator, decimalFormatter, processedDeviceStatusData
        )
    }

    val manageViewModel: ManageViewModel by lazy {
        ManageViewModel(
            rh, activePlugin, profileFunction, loop, screen.config, processedTbrEbData, persistenceLayer,
            rxBus, screen.dateUtil, screen.preferences, batchExecutor, nsClient, visibilityContext,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    /** Publishes a BG reading, so the BG circle has something to draw. */
    fun withBg(
        bgText: String = BG_TEXT,
        deltaText: String? = DELTA_TEXT,
        timestamp: Long = BG_TIMESTAMP
    ) {
        cache.bgInfoFlow.value = BgInfoData(
            bgValue = 120.0, bgText = bgText, bgRange = BgRange.IN_RANGE,
            isOutdated = false, timestamp = timestamp, trendArrow = TrendArrow.FLAT,
            trendDescription = "Flat", delta = 2.0, deltaText = deltaText,
            shortAvgDelta = null, shortAvgDeltaText = null, longAvgDelta = null, longAvgDeltaText = null
        )
    }

    /**
     * Waits until [StatusViewModel] has finished its first refresh.
     *
     * `refreshState()` really does hop to the IO dispatcher, so the status card is empty for a
     * moment after construction, and `OverviewStatusSection` returns without drawing anything while
     * it is. Blocking here means a test asserting on the status card is not racing that.
     */
    fun awaitStatusItems(): StatusViewModel = statusViewModel.also { vm ->
        runBlocking {
            withTimeout(5_000) { vm.uiState.first { it.sensorStatus != null } }
        }
    }

    companion object {

        const val NOW = 1_700_000_000_000L
        const val BG_TIMESTAMP = NOW - 300_000L

        /** Distinct on purpose: the BG circle's "time ago" and the clock's "time ago" are different widgets. */
        const val TIME_AGO = "5 min"
        const val CLOCK_TIME = "21:33"
        const val CLOCK_AGO = " 5'"
        const val BG_TEXT = "120"
        const val DELTA_TEXT = "+2"
    }
}

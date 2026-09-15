package app.aaps.pump.insight.compose

import android.content.Context
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.insight.InsightPlugin
import app.aaps.pump.insight.R
import app.aaps.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.descriptors.ActiveBasalRate
import app.aaps.pump.insight.descriptors.BatteryStatus
import app.aaps.pump.insight.descriptors.CartridgeStatus
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.descriptors.TotalDailyDose
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR

/**
 * Unit test for [InsightOverviewState], the Insight pump overview state holder. Unlike other pump
 * overviews it is NOT a ViewModel and does not use viewModelScope — [InsightComposeContent] builds
 * it via `remember`. It exposes `uiState: StateFlow<PumpOverviewUiState>` whose value is computed at
 * construction by `buildUiState()`, which only reads [InsightPlugin.connectionService] and
 * `insightPlugin.isInitialized()`. The rx subscription lives in `start()`, which the tests never
 * call, so no rx / dispatcher wiring is needed. Rendering itself is covered by core:ui's
 * PumpOverviewScreenTest (the OVERVIEW screen just delegates to PumpOverviewScreen).
 */
internal class InsightOverviewStateTest {

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var context: Context
    @Mock private lateinit var ch: ConcentrationHelper

    private val insightPlugin: InsightPlugin = mock()
    private val service: InsightConnectionService = mock()

    // appScope is only used by action onClick handlers, never at construction / in buildUiState().
    private val appScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    private fun createState(scope: CoroutineScope = appScope) = InsightOverviewState(
        insightPlugin = insightPlugin,
        aapsLogger = aapsLogger,
        rh = rh,
        rxBus = rxBus,
        dateUtil = dateUtil,
        commandQueue = commandQueue,
        context = context,
        ch = ch,
        appScope = scope
    )

    @Test
    fun noConnectionService_producesEmptyState() {
        whenever(insightPlugin.connectionService).thenReturn(null)
        whenever(insightPlugin.isInitialized()).thenReturn(false)

        val state = createState().uiState.value

        // service == null -> no info rows; !isInitialized -> no primary actions; null service -> no management actions
        assertThat(state.infoRows).isEmpty()
        assertThat(state.primaryActions).isEmpty()
        assertThat(state.managementActions).isEmpty()
        assertThat(state.statusBanner).isNull()
    }

    @Test
    fun pairedButNotInitialized_buildsStatusRow_andOffersUnpair() {
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(false)   // skips the whole isInitialized info block
        whenever(service.state).thenReturn(InsightState.DISCONNECTED)
        whenever(service.isPaired).thenReturn(true)
        // service.lastConnected defaults to 0L on the mock -> "last connected" row skipped.
        // pumpSystemIdentification / pumpFirmwareVersions / bluetoothAddress default to null -> identity rows skipped.

        whenever(rh.gs(R.string.insight_status)).thenReturn("Status")
        whenever(rh.gs(CoreUiR.string.disconnected)).thenReturn("Disconnected")
        whenever(rh.gs(R.string.unpair)).thenReturn("Unpair")

        val state = createState().uiState.value

        val rows = state.infoRows.filterIsInstance<PumpInfoRow>()
        assertThat(rows).isNotEmpty()
        val statusRow = rows.first { it.label == "Status" }
        assertThat(statusRow.value).isEqualTo("Disconnected")
        // !isInitialized -> no primary actions
        assertThat(state.primaryActions).isEmpty()
        // paired -> management offers Unpair, not Pairing
        assertThat(state.managementActions.map { it.label }).containsExactly("Unpair")
    }

    // ---- the isInitialized block: the pump readings shown once the driver is up ----

    /**
     * Answers every plain string lookup with a stable marker built from the id, so a row can be
     * found by the resource it was built from without pinning the English text.
     */
    private fun stubPlainStrings() {
        whenever(rh.gs(anyInt())).thenAnswer { "r" + it.arguments[0] }
    }

    private fun label(id: Int) = "r$id"

    private fun connectedAndInitialized() {
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(true)
        whenever(service.state).thenReturn(InsightState.CONNECTED)
        whenever(service.isPaired).thenReturn(false)   // keeps the identity rows out of the way
        // A real StateFlow, not a mock: buildInfoRows reads .value on it without a null check.
        whenever(insightPlugin.lastBolusAmount).thenReturn(MutableStateFlow(null))
    }

    @Test
    fun aFullyInitializedPumpShowsEveryReadingItHas() {
        stubPlainStrings()
        connectedAndInitialized()
        whenever(insightPlugin.operatingMode).thenReturn(OperatingMode.STARTED)
        whenever(insightPlugin.batteryStatus).thenReturn(BatteryStatus().also { it.batteryAmount = 75 })
        whenever(insightPlugin.cartridgeStatus).thenReturn(CartridgeStatus().also {
            it.isInserted = true
            it.remainingAmount = 123.4
        })
        whenever(insightPlugin.totalDailyDose).thenReturn(TotalDailyDose().also {
            it.bolus = 10.0
            it.basal = 20.0
            it.bolusAndBasal = 30.0
        })
        whenever(insightPlugin.activeBasalRate).thenReturn(ActiveBasalRate().also {
            it.activeBasalRate = 0.8
            it.activeBasalProfileName = "Profile A"
        })
        whenever(rh.gs(eq(CoreUiR.string.format_percent), any())).thenReturn("75%")
        whenever(ch.insulinAmountString(any())).thenReturn("1.23 U")
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.80 U/h")

        val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()

        val labels = rows.map { it.label }
        assertThat(labels).contains(label(R.string.operating_mode))
        assertThat(labels).contains(label(CoreUiR.string.battery_label))
        assertThat(labels).contains(label(R.string.reservoir_level))
        assertThat(labels).contains(label(R.string.tdd_bolus))
        assertThat(labels).contains(label(R.string.tdd_basal))
        assertThat(labels).contains(label(CoreUiR.string.tdd_total))
        assertThat(labels).contains(label(CoreUiR.string.base_basal_rate_label))
        assertThat(rows.first { it.label == label(CoreUiR.string.battery_label) }.value).isEqualTo("75%")
        // The basal row pairs the rate with the profile name it came from.
        assertThat(rows.first { it.label == label(CoreUiR.string.base_basal_rate_label) }.value)
            .isEqualTo("0.80 U/h (Profile A)")
    }

    @Test
    fun aPumpWithNoReadingsYetShowsOnlyItsStatus() {
        stubPlainStrings()
        connectedAndInitialized()
        // Every reading is null on the mock, so each let{} block is skipped.

        val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()

        assertThat(rows.map { it.label }).containsExactly(label(R.string.insight_status))
    }

    @Test
    fun anEmptyCartridgeSaysSoInsteadOfShowingAnAmount() {
        stubPlainStrings()
        connectedAndInitialized()
        whenever(insightPlugin.cartridgeStatus).thenReturn(CartridgeStatus().also { it.isInserted = false })

        val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()

        assertThat(rows.first { it.label == label(R.string.reservoir_level) }.value)
            .isEqualTo(label(R.string.not_inserted))
    }

    @Test
    fun everyOperatingModeHasItsOwnText() {
        val seen = mutableMapOf<OperatingMode, String>()
        for (mode in OperatingMode.entries) {
            stubPlainStrings()
            connectedAndInitialized()
            whenever(insightPlugin.operatingMode).thenReturn(mode)

            val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()
            seen[mode] = rows.first { it.label == label(R.string.operating_mode) }.value
        }

        // Three modes, three different texts: a copy-paste would show "started" for a stopped pump.
        assertThat(seen.values.toSet()).hasSize(OperatingMode.entries.size)
    }

    @Test
    fun recoveringShowsHowLongTheRecoveryLasts() {
        stubPlainStrings()
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(false)
        whenever(service.state).thenReturn(InsightState.RECOVERING)
        whenever(service.recoveryDuration).thenReturn(12_000L)
        whenever(service.isPaired).thenReturn(false)
        whenever(rh.gs(eq(CoreUiR.string.secs), any())).thenReturn("12 s")

        val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()

        assertThat(rows.map { it.label }).contains(label(R.string.recovery_duration))
        assertThat(rows.first { it.label == label(R.string.recovery_duration) }.value).isEqualTo("12 s")
    }

    @Test
    fun aPumpThatWasConnectedBeforeShowsWhenThatWas() {
        stubPlainStrings()
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(false)
        whenever(service.state).thenReturn(InsightState.DISCONNECTED)
        whenever(service.isPaired).thenReturn(false)
        whenever(service.lastConnected).thenReturn(System.currentTimeMillis() - 5 * 60_000L)
        whenever(dateUtil.minAgo(any(), any())).thenReturn("5 min ago")
        whenever(dateUtil.timeString(any<Long>())).thenReturn("12:00")
        whenever(rh.gs(eq(R.string.last_connection), any(), any())).thenReturn("12:00 (5 min ago)")

        val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()

        assertThat(rows.map { it.label }).contains(label(R.string.last_connected))
    }

    // ---- the actions offered on the overview ----

    /** Collects the events the state emits while [block] runs; they are not replayed to a late collector. */
    private fun eventsDuring(state: InsightOverviewState, block: () -> Unit): List<InsightOverviewEvent> {
        val received = mutableListOf<InsightOverviewEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { state.events.collect { received.add(it) } }
        block()
        job.cancel()
        return received
    }

    @Test
    fun anInitializedPumpOffersRefresh() {
        stubPlainStrings()
        connectedAndInitialized()

        val actions = createState().uiState.value.primaryActions

        assertThat(actions.map { it.label }).containsExactly(label(CoreUiR.string.refresh))
        assertThat(actions.single().enabled).isTrue()
    }

    @Test
    fun clickingRefreshAsksTheQueueForAStatusRead() = runTest {
        stubPlainStrings()
        connectedAndInitialized()
        val state = createState()

        state.uiState.value.primaryActions.single().onClick()

        verify(commandQueue).readStatus("InsightRefreshButton")
    }

    /**
     * The button goes flat while the read is in flight, so it cannot be pressed twice. The state is
     * given a scope that does not run its work straight away, which is what leaves the read pending.
     */
    @Test
    fun refreshIsDisabledWhileTheReadIsStillRunning() = runTest {
        stubPlainStrings()
        connectedAndInitialized()
        val state = createState(scope = CoroutineScope(StandardTestDispatcher(testScheduler)))

        state.uiState.value.primaryActions.single().onClick()

        assertThat(state.uiState.value.primaryActions.single().enabled).isFalse()
    }

    @Test
    fun theTbrOverNotificationActionSaysWhatItWillDo() {
        for (enabled in listOf(true, false)) {
            stubPlainStrings()
            connectedAndInitialized()
            whenever(insightPlugin.tBROverNotificationBlock)
                .thenReturn(TBROverNotificationBlock().also { it.isEnabled = enabled })

            val labels = createState().uiState.value.primaryActions.map { it.label }

            val expected = if (enabled) R.string.disable_tbr_over_notification else R.string.enable_tbr_over_notification
            assertThat(labels).contains(label(expected))
        }
    }

    @Test
    fun aPumpWithoutTheNotificationSettingOffersOnlyRefresh() {
        stubPlainStrings()
        connectedAndInitialized()
        // tBROverNotificationBlock is null on the mock.

        val actions = createState().uiState.value.primaryActions

        assertThat(actions).hasSize(1)
    }

    @Test
    fun anUnpairedPumpOffersPairingInsteadOfUnpair() {
        stubPlainStrings()
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(false)
        whenever(service.state).thenReturn(InsightState.NOT_PAIRED)
        whenever(service.isPaired).thenReturn(false)

        val actions = createState().uiState.value.managementActions

        assertThat(actions.map { it.label }).containsExactly(label(R.string.insight_pairing))
    }

    /** Unpairing is destructive, so the action only asks - the screen confirms it. */
    @Test
    fun clickingUnpairAsksRatherThanUnpairing() {
        stubPlainStrings()
        connectedAndInitialized()
        whenever(service.isPaired).thenReturn(true)
        val state = createState()

        val events = eventsDuring(state) { state.uiState.value.managementActions.single().onClick() }

        assertThat(events).containsExactly(InsightOverviewEvent.RequestUnpair)
        // Asking is all it does: the keys are only dropped once performUnpair() is called.
        verify(service, never()).reset()
    }

    @Test
    fun clickingPairingAsksToStartPairing() {
        stubPlainStrings()
        whenever(insightPlugin.connectionService).thenReturn(service)
        whenever(insightPlugin.isInitialized()).thenReturn(false)
        whenever(service.state).thenReturn(InsightState.NOT_PAIRED)
        whenever(service.isPaired).thenReturn(false)
        val state = createState()

        val events = eventsDuring(state) { state.uiState.value.managementActions.single().onClick() }

        assertThat(events).containsExactly(InsightOverviewEvent.StartPairing)
    }

    /** A connected pump has nothing to say about when it was last connected. */
    @Test
    fun aConnectedPumpDoesNotShowALastConnectedRow() {
        stubPlainStrings()
        connectedAndInitialized()
        whenever(service.lastConnected).thenReturn(System.currentTimeMillis() - 5 * 60_000L)

        val rows = createState().uiState.value.infoRows.filterIsInstance<PumpInfoRow>()

        assertThat(rows.map { it.label }).doesNotContain(label(R.string.last_connected))
    }
}

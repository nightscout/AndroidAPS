package app.aaps.pump.aaruy.compose

import org.mockito.kotlin.doAnswer
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.AaruyStrings
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.services.AaruyBLE
import app.aaps.pump.aaruy.services.AaruyConfirmAlarmsCommand
import app.aaps.pump.aaruy.services.AaruyUnpair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.aaps.core.ui.CoreUiStrings

@OptIn(ExperimentalCoroutinesApi::class)
class AaruyOverviewViewModelTest {

    private val rh: ResourceHelper = mock()
    private val rxBus: RxBus = mock()
    private val activePump: PumpWithConcentration = mock()
    private val activePlugin: ActivePlugin = mock()
    private val ble: AaruyBLE = mock()
    private val unpair: AaruyUnpair = mock()
    private val ch: ConcentrationHelper = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), rh, mock<Preferences>(), mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        doAnswer { "s${it.arguments[0]}" }.whenever(rh).gs(any<TextRef>())
        doAnswer { "s${it.arguments[0]}" }.whenever(rh).gs(any<TextRef>(), anyVararg<Any>())
        whenever(ch.insulinAmountString(any())).thenReturn("1 U")
        whenever(ch.insulinAmountAgoString(any(), any())).thenReturn("1 U")
        whenever(rxBus.toFlow(EventPumpStatusChanged::class)).thenReturn(emptyFlow())
        whenever(rxBus.toFlow(EventQueueChanged::class)).thenReturn(emptyFlow())
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePump.baseBasalRate).thenReturn(PumpRate(0.15))
        whenever(ch.basalRateString(any(), any(), any())).thenReturn("0.15 U/h")
        whenever(ble.getShortDeviceName()).thenReturn("X10001Y")
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val commandQueue: CommandQueue = mock()

    private fun viewModel(configured: Boolean = true, initialized: Boolean = true): AaruyOverviewViewModel {
        whenever(activePump.isConfigured()).thenReturn(configured)
        whenever(activePump.isInitialized()).thenReturn(initialized)
        return AaruyOverviewViewModel(rh, rxBus, commandQueue, mock<DateUtil>(), pump, ble, unpair, activePlugin, ch)
    }

    private fun state(configured: Boolean = true, initialized: Boolean = true): PumpOverviewUiState = viewModel(configured, initialized).uiState.value

    private fun queueAnswers(success: Boolean) = runBlocking {
        val result = mock<PumpEnactResult>()
        whenever(result.success).thenReturn(success)
        whenever(commandQueue.customCommand(any())).thenReturn(result)
    }

    private fun PumpOverviewUiState.row(label: TextRef) = infoRows.filterIsInstance<PumpInfoRow>().single { it.label == "s$label" }
    private fun PumpOverviewUiState.actions() = (primaryActions + managementActions).filter { it.visible }.map { it.label }

    @Test
    fun anUnpairedPumpOffersOnlyPairing() {
        val state = state(configured = false, initialized = false)
        assertTrue(state.infoRows.isEmpty())
        assertEquals(listOf("s${CoreUiStrings.pump_pair}"), state.actions())
    }

    @Test
    fun aRunningPumpOffersRefreshHistoryChangeReservoirAndUnpair() {
        pump.pumpState = AaruyPumpState.BASALRATE_RUNNING
        val state = state()
        assertEquals(StatusLevel.NORMAL, state.row(AaruyStrings.pump_state_label).level)
        assertFalse(state.row(AaruyStrings.active_alarms_label).visible)
        assertEquals(
            listOf("s${CoreUiStrings.refresh}", "s${CoreUiStrings.pump_history}", "s${AaruyStrings.replace_label}", "s${CoreUiStrings.pump_unpair}"),
            state.actions()
        )
    }

    /** A stopped pump gives no insulin, and it has no screen of its own to say so. */
    @Test
    fun aStoppedPumpIsShownInRed() {
        pump.pumpState = AaruyPumpState.STOP_RUNNING
        assertEquals(StatusLevel.CRITICAL, state().row(AaruyStrings.pump_state_label).level)
    }

    /**
     * Seen on a real pump: confirming stops its beeping. The command queue opens the link for it, as for every command,
     * so it is offered when no link is up. It used to be sent straight to the pump, and only while a link was up.
     */
    @Test
    fun anActiveAlarmIsConfirmedThroughTheQueue() {
        pump.pumpState = AaruyPumpState.STOP_RUNNING
        pump.connectionState = ConnectionState.DISCONNECTED
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        queueAnswers(success = true)
        val state = state()
        assertTrue(state.row(AaruyStrings.active_alarms_label).visible)
        (state.primaryActions + state.managementActions).single { it.label == "s${AaruyStrings.confirm_alarms_label}" }.onClick()
        runBlocking { verify(commandQueue).customCommand(any<AaruyConfirmAlarmsCommand>()) }
        verify(ble, never()).confirmAlarms()
    }

    /** The driver removes the pump, so leaving the screen does not stop it (acceptance D9). Nothing more is asked. */
    @Test
    fun theUnpairIsTheDriversNotTheScreens() {
        val events = viewModel().eventsOf { unpair() }
        verify(unpair).start()
        assertTrue(events.isEmpty(), "$events")
    }

    private fun AaruyOverviewViewModel.eventsOf(action: AaruyOverviewViewModel.() -> Unit): List<AaruyOverviewEvent> {
        val events = mutableListOf<AaruyOverviewEvent>()
        val collector = CoroutineScope(UnconfinedTestDispatcher()).launch { this@eventsOf.events.collect { events.add(it) } }
        action()
        collector.cancel()
        return events
    }

    /** Starting the change would end the bolus. */
    @Test
    fun noReservoirChangeDuringABolus() {
        pump.pumpState = AaruyPumpState.LARGEDOSE_RUNNING
        assertFalse("s${AaruyStrings.replace_label}" in state().actions())
    }

    @Test
    fun aLowReservoirIsAWarningAndAnEmptyOneIsCritical() {
        pump.pumpState = AaruyPumpState.BASALRATE_RUNNING
        pump.reservoir = 35.0
        assertEquals(StatusLevel.WARNING, state().row(CoreUiStrings.reservoir_label).level)
        pump.reservoir = 15.0
        assertEquals(StatusLevel.CRITICAL, state().row(CoreUiStrings.reservoir_label).level)
        pump.reservoir = 200.0
        assertEquals(StatusLevel.NORMAL, state().row(CoreUiStrings.reservoir_label).level)
    }

    /** After an app start, until the first status. They showed 0 U and 0 % in red. */
    @Test
    fun aReservoirAndBatteryThatAreNotKnownShowADash() {
        pump.pumpState = AaruyPumpState.BASALRATE_RUNNING
        pump.reservoir = null
        pump.remainBattery = null
        val state = state()
        assertEquals("-", state.row(CoreUiStrings.reservoir_label).value)
        assertEquals(StatusLevel.NORMAL, state.row(CoreUiStrings.reservoir_label).level)
        assertEquals("-", state.row(CoreUiStrings.battery_label).value)
        assertEquals(StatusLevel.NORMAL, state.row(CoreUiStrings.battery_label).level)
    }
}

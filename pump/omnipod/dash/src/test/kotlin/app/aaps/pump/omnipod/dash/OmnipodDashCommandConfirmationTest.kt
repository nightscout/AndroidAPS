package app.aaps.pump.omnipod.dash

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.state.CommandConfirmed
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.queue.command.CommandDisableSuspendAlerts
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.after
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * What the plugin does when the pod confirms a command.
 *
 * Setting the time (after a time zone change) is sent to the pod as suspend + new basal program, the same
 * as a profile change. The suspend starts a "pump suspended" temp basal in AAPS and the pod's "delivery
 * suspended" alert, which beeps after 20 minutes. The confirmation must end both, or the pod beeps while
 * basal is running and AAPS still shows the pump as suspended (#2982).
 */
class OmnipodDashCommandConfirmationTest {

    private lateinit var podStateManager: OmnipodDashPodStateManager
    private lateinit var history: DashHistory
    private lateinit var pumpSync: PumpSync
    private lateinit var commandQueue: CommandQueue
    private lateinit var rxBus: RxBus
    private lateinit var notificationManager: NotificationManager
    private lateinit var sut: OmnipodDashPumpPlugin

    private val basalProgram = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))

    @BeforeEach
    fun setUp() {
        podStateManager = mock()
        history = mock()
        pumpSync = mock()
        commandQueue = mock()
        rxBus = mock()
        notificationManager = mock()
        sut = OmnipodDashPumpPlugin(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<ResourceHelper>(),
            preferences = mock<Preferences>(),
            commandQueue = commandQueue,
            omnipodManager = mock<OmnipodDashManager>(),
            podStateManager = podStateManager,
            history = history,
            pumpSync = pumpSync,
            rxBus = rxBus,
            aapsSchedulers = mock<AapsSchedulers>(),
            uiInteraction = mock<UiInteraction>(),
            notificationManager = notificationManager,
            pumpEnactResultProvider = { mock<PumpEnactResult>() },
            bolusProgressData = mock<BolusProgressData>(),
            dashHistoryDatabase = mock<DashHistoryDatabase>(),
            protectionCheck = mock<ProtectionCheck>(),
            blePreCheck = mock<BlePreCheck>()
        )
    }

    @Test
    fun `a confirmed time change ends the suspend and turns the suspend alert off`() = runBlocking<Unit> {
        givenHistoryEntry(OmnipodCommandType.SET_TIME)

        sut.handleCommandConfirmation(confirmation(success = true))

        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(eq(CREATED_AT), eq(HISTORY_ID), eq(PumpType.OMNIPOD_DASH), any(), any()) }
        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CommandDisableSuspendAlerts>()) }
        verify(podStateManager).basalProgram = basalProgram
        verify(notificationManager).dismiss(NotificationId.OMNIPOD_POD_SUSPENDED)
        verify(notificationManager).dismiss(NotificationId.OMNIPOD_TIME_OUT_OF_SYNC)
    }

    /** A profile change asked again by a late confirmation is only for a real profile change. */
    @Test
    fun `a confirmed time change does not ask for a profile change`() = runBlocking<Unit> {
        givenHistoryEntry(OmnipodCommandType.SET_TIME)

        sut.handleCommandConfirmation(confirmation(success = true))

        verify(rxBus, never()).send(any<EventProfileChangeRequested>())
    }

    @Test
    fun `a failed time change changes nothing`() = runBlocking<Unit> {
        givenHistoryEntry(OmnipodCommandType.SET_TIME)

        sut.handleCommandConfirmation(confirmation(success = false))

        verifyBlocking(pumpSync, never()) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
        verifyBlocking(commandQueue, after(300).never()) { customCommand(any()) }
    }

    /** The shared branch must keep the profile change behaviour as it was. */
    @Test
    fun `a late confirmed profile change still asks for the profile change again`() = runBlocking<Unit> {
        givenHistoryEntry(OmnipodCommandType.SET_BASAL_PROFILE)

        sut.handleCommandConfirmation(confirmation(success = true))

        verify(rxBus).send(any<EventProfileChangeRequested>())
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(eq(CREATED_AT), eq(HISTORY_ID), eq(PumpType.OMNIPOD_DASH), any(), any()) }
        verifyBlocking(commandQueue, timeout(2_000)) { customCommand(any<CommandDisableSuspendAlerts>()) }
    }

    private fun givenHistoryEntry(commandType: OmnipodCommandType) {
        whenever(history.getById(HISTORY_ID)).thenReturn(
            HistoryRecord(
                id = HISTORY_ID, createdAt = CREATED_AT, date = CREATED_AT, commandType = commandType, initialResult = InitialResult.SENT,
                record = null, totalAmountDelivered = null, resolvedResult = null, resolvedAt = null
            )
        )
    }

    private fun confirmation(success: Boolean) = CommandConfirmed(
        OmnipodDashPodStateManager.ActiveCommand(
            sequence = 1, createdRealtime = 0L, historyId = HISTORY_ID, sendError = null, basalProgram = basalProgram, tempBasal = null, requestedBolus = null
        ),
        success
    )

    private companion object {

        const val HISTORY_ID = 7L
        const val CREATED_AT = 1_000L
    }
}

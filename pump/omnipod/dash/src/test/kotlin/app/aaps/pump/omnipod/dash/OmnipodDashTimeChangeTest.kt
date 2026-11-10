package app.aaps.pump.omnipod.dash

import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.state.CommandConfirmed
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.queue.command.CommandHandleTimeChange
import app.aaps.pump.omnipod.common.util.PodTimeZone
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OmnipodDashTimeChangeTest : TestBaseWithProfile() {

    private val logger = mock<AAPSLogger>()
    private val state = mock<OmnipodDashPodStateManager>()
    private val manager = mock<OmnipodDashManager>()
    private val history = mock<DashHistory>()
    private val sync = mock<PumpSync>()
    private val queue = mock<CommandQueue>()
    private lateinit var plugin: OmnipodDashPumpPlugin
    private var delivery = DeliveryStatus.BASAL_ACTIVE
    private var active: OmnipodDashPodStateManager.ActiveCommand? = null
    private var temp: OmnipodDashPodStateManager.TempBasal? = null
    private var inProgress = false
    private var zone = PodTimeZone.now().copy(offset = PodTimeZone.now().offset - 3600000)
    private var expectedTemp: PumpSync.PumpState.TemporaryBasal? = null
    private var basal: BasalProgram? = null
    private var confirmSuccess = true
    private val records = mutableMapOf<Long, HistoryRecord>()

    private fun loopAllowed(): Boolean {
        var allowed = true
        val constraint = mock<Constraint<Boolean>>()
        doAnswer { allowed = it.getArgument(0); constraint }.whenever(constraint).set(any(), anyOrNull(), any())
        plugin.isLoopInvocationAllowed(constraint)
        return allowed
    }

    @BeforeEach
    fun setup() = runBlocking<Unit> {
        plugin = OmnipodDashPumpPlugin(
            logger, rh, preferences, queue, manager, state, history, sync, rxBus,
            aapsSchedulers, mock(), notificationManager, pumpEnactResultProvider, mock(), mock(), mock(), mock()
        )
        whenever(state.isPodRunning).thenReturn(true)
        whenever(state.isActivationCompleted).thenReturn(true)
        whenever(state.deliveryStatus).thenAnswer { delivery }
        whenever(state.isSuspended).thenAnswer { delivery.suspended() }
        whenever(state.tempBasalActive).thenAnswer { delivery.tempBasalActive() }
        whenever(state.tempBasal).thenAnswer { temp }
        doAnswer { temp = it.getArgument(0); Unit }.whenever(state).tempBasal = anyOrNull()
        whenever(state.timeZoneOffset).thenAnswer { zone.offset }
        whenever(state.timeChangeInProgress).thenAnswer { inProgress }
        doAnswer { inProgress = it.getArgument(0); Unit }.whenever(state).timeChangeInProgress = any()
        doAnswer { zone = it.getArgument(0); Unit }.whenever(state).confirmTimeZone(any())
        whenever(state.activeCommand).thenAnswer { active }
        whenever(state.basalProgram).thenAnswer { basal }
        doAnswer { basal = it.getArgument(0); Unit }.whenever(state).basalProgram = anyOrNull()
        whenever(state.observeNoActiveCommand()).thenReturn(Completable.defer {
            if (active == null) Completable.complete() else Completable.error(IllegalStateException("Unconfirmed"))
        })
        whenever(state.createActiveCommand(any(), anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer {
            val id = it.getArgument<Long>(0)
            val basal = it.getArgument<BasalProgram?>(1)
            val newTemp = it.getArgument<OmnipodDashPodStateManager.TempBasal?>(2)
            Single.fromCallable {
                OmnipodDashPodStateManager.ActiveCommand(1, 0, historyId = id, sendError = null,
                    basalProgram = basal, tempBasal = newTemp, requestedBolus = null).also { command -> active = command }
            }
        }
        whenever(state.updateActiveCommand()).thenReturn(Maybe.defer {
            val command = active
            active = null
            if (command == null) Maybe.empty() else Maybe.just(CommandConfirmed(command, confirmSuccess))
        })
        whenever(history.updateFromState(any())).thenReturn(Completable.complete())
        whenever(history.createRecord(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer {
                val commandType = it.getArgument<OmnipodCommandType>(0)
                Single.fromCallable {
                    val id = records.size.toLong() + 1
                    val now = System.currentTimeMillis()
                    records[id] = HistoryRecord(id, now, now, commandType, InitialResult.SENT, null, null, null, null)
                    id
                }
            }
        whenever(history.getById(any())).thenAnswer { records.getValue(it.getArgument(0)) }
        val profile = mock<PumpProfile>()
        whenever(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, 1.0)))
        whenever(sync.expectedPumpState()).thenAnswer { PumpSync.PumpState(expectedTemp, null, null, profile, "12345") }
        whenever(sync.syncTemporaryBasalWithPumpId(any(), any(), any(), any(), anyOrNull(), any(), any(), any())).thenAnswer {
            expectedTemp = PumpSync.PumpState.TemporaryBasal(
                it.getArgument(0), it.getArgument(2), it.getArgument<PumpRate>(1).cU, true,
                it.getArgument(4), 1, it.getArgument(5)
            )
            true
        }
        whenever(sync.syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any())).thenAnswer { expectedTemp = null; true }
        whenever(manager.getStatus(any())).thenReturn(Observable.empty())
        whenever(manager.suspendDelivery(any())).thenReturn(Observable.defer {
            delivery = DeliveryStatus.SUSPENDED
            Observable.just(PodEvent.CommandSent(mock()))
        })
        whenever(manager.setBasalProgram(any(), any())).thenReturn(Observable.defer {
            active?.basalTimeZone = PodTimeZone.now()
            delivery = DeliveryStatus.BASAL_ACTIVE
            Observable.empty()
        })
        whenever(manager.setTempBasal(any(), any(), any())).thenReturn(Observable.defer {
            delivery = DeliveryStatus.TEMP_BASAL_ACTIVE
            Observable.just(PodEvent.CommandSent(mock()))
        })
    }

    @ParameterizedTest
    @ValueSource(ints = [3600000, -3600000])
    fun `both DST directions unblock loop only after confirmed time correction`(difference: Int) = runBlocking<Unit> {
        zone = PodTimeZone.now().let { it.copy(offset = it.offset - difference) }
        assertThat(loopAllowed()).isFalse()

        val result = plugin.executeCustomCommand(CommandHandleTimeChange(false))

        assertThat(result.success).isTrue()
        assertThat(zone.offset).isEqualTo(PodTimeZone.now().offset)
        assertThat(inProgress).isFalse()
        assertThat(loopAllowed()).isTrue()
        verify(manager).setBasalProgram(any(), any())
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 0.7, 1.5, 1.0])
    fun `a DST correction suspends and loses any active temp basal, like Medtrum accepts`(rate: Double) = runBlocking<Unit> {
        val start = System.currentTimeMillis() - 60000
        temp = OmnipodDashPodStateManager.TempBasal(start, rate, 30)
        expectedTemp = PumpSync.PumpState.TemporaryBasal(start, 30 * 60000L, rate, true, PumpSync.TemporaryBasalType.NORMAL, 1, 1)
        delivery = DeliveryStatus.TEMP_BASAL_ACTIVE

        val result = plugin.executeCustomCommand(CommandHandleTimeChange(false))

        assertThat(result.success).isTrue()
        assertThat(temp).isNull()
        verify(manager, never()).setTempBasal(any(), any(), any())
        assertThat(inProgress).isFalse()
    }

    @Test
    fun `reported suspend counts the old rate then keeps the same zero inside and outside`() = runBlocking<Unit> {
        var zeroAtResume: OmnipodDashPodStateManager.TempBasal? = null
        var reportedAtResume: PumpSync.PumpState.TemporaryBasal? = null
        whenever(manager.setBasalProgram(any(), any())).thenReturn(Observable.defer {
            zeroAtResume = temp
            reportedAtResume = expectedTemp
            delivery = DeliveryStatus.BASAL_ACTIVE
            Observable.empty()
        })

        val result = plugin.executeCustomCommand(CommandHandleTimeChange(false))

        assertThat(result.success).isTrue()
        val reported = requireNotNull(reportedAtResume)
        val zero = OmnipodDashPodStateManager.TempBasal(reported.timestamp, 0.0, (reported.duration / 60000).toShort())
        assertThat(zeroAtResume).isEqualTo(zero)
        assertThat(reported.type).isEqualTo(PumpSync.TemporaryBasalType.PUMP_SUSPEND)
        val order = inOrder(state)
        order.verify(state).updateBasalExpected(reported.timestamp)
        order.verify(state).tempBasal = zero
        assertThat(temp).isNull()
        assertThat(expectedTemp).isNull()
    }

    @Test
    fun `a nonzero suspend rate also updates the expected basal at the reported start`() = runBlocking<Unit> {
        whenever(state.updateActiveCommand()).thenReturn(Maybe.empty())

        plugin.setTempBasalAbsolute(1.0, 30, true, PumpSync.TemporaryBasalType.PUMP_SUSPEND)

        val reported = requireNotNull(expectedTemp)
        verify(state).updateBasalExpected(reported.timestamp)
        assertThat(temp).isEqualTo(OmnipodDashPodStateManager.TempBasal(reported.timestamp, 1.0, 30))
    }

    @Test
    fun `a denied time update keeps the loop blocked for a retry`() = runBlocking<Unit> {
        confirmSuccess = false

        val result = plugin.executeCustomCommand(CommandHandleTimeChange(false))

        assertThat(result.success).isTrue()
        assertThat(inProgress).isTrue()
        assertThat(loopAllowed()).isFalse()
    }

    @Test
    fun `an active bolus or a user suspension prevents automatic reprogramming`() = runBlocking<Unit> {
        delivery = DeliveryStatus.BOLUS_AND_BASAL_ACTIVE
        plugin.executeCustomCommand(CommandHandleTimeChange(false))
        delivery = DeliveryStatus.SUSPENDED
        plugin.executeCustomCommand(CommandHandleTimeChange(false))

        verify(manager, never()).suspendDelivery(any())
        verify(manager, never()).setBasalProgram(any(), any())
    }

    @Test
    fun `DST callback queues correction once and a missed callback is found by polling`() = runBlocking<Unit> {
        whenever(queue.customCommand(any<CommandHandleTimeChange>())).thenAnswer {
            plugin.executeCustomCommand(it.getArgument(0))
        }

        plugin.timezoneOrDSTChanged(TimeChangeType.DSTStarted)
        plugin.checkTimeChange()

        verify(queue, times(1)).customCommand(any<CommandHandleTimeChange>())
        zone = zone.copy(offset = zone.offset - 3600000)
        plugin = OmnipodDashPumpPlugin(
            aapsLogger, rh, preferences, queue, manager, state, history, sync, rxBus,
            aapsSchedulers, mock(), notificationManager, pumpEnactResultProvider, mock(), mock(), mock(), mock()
        )
        plugin.checkTimeChange()
        verify(queue, times(2)).customCommand(any<CommandHandleTimeChange>())
    }
}

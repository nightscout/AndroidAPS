package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.coordinator.CarelevoAlarmClearCoordinator
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CopyOnWriteArrayList

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoAlarmActionHandlerTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var alarmUseCase: CarelevoAlarmInfoUseCase
    @Mock lateinit var alarmClearCoordinator: CarelevoAlarmClearCoordinator

    private lateinit var sut: CarelevoAlarmActionHandler

    private fun alarm(
        id: String = "alarm-1",
        cause: AlarmCause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        createdAt: String = "2026-07-16T10:00:00"
    ): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = id,
            alarmType = cause.alarmType,
            cause = cause,
            value = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            isAcknowledged = false
        )

    @BeforeEach
    fun setUp() {
        // acknowledgeAlarm and clearAlarms are Unit suspend functions: an unstubbed mock already succeeds.
        sut = CarelevoAlarmActionHandler(aapsLogger, commandQueue, alarmUseCase, alarmClearCoordinator)
        // The handler's coroutines run on the caller's thread, so most state changes are done when the call returns.
        sut.dispatcher = Dispatchers.Unconfined
    }

    /** Await a state change that may come later (for example after a pending read ends), with a bounded timeout. */
    private fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) = runBlocking {
        withTimeout(timeoutMs) {
            while (!condition()) delay(20)
        }
    }

    @Test
    fun `loadActiveAlarms fills the queue sorted by severity tier then creation time`() {
        val notice = alarm(id = "notice", cause = AlarmCause.ALARM_NOTICE_LOW_INSULIN, createdAt = "2026-07-16T08:00:00")
        val warningLate = alarm(id = "warning-late", cause = AlarmCause.ALARM_WARNING_PUMP_CLOGGED, createdAt = "2026-07-16T09:00:00")
        val warningEarly = alarm(id = "warning-early", cause = AlarmCause.ALARM_WARNING_LOW_INSULIN, createdAt = "2026-07-16T08:30:00")
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(notice, warningLate, warningEarly))

        sut.loadActiveAlarms()

        assertThat(sut.alarmQueue.value.map { it.alarmId })
            .containsExactly("warning-early", "warning-late", "notice")
            .inOrder()
    }

    @Test
    fun `acknowledgeAndRemoveAlarm persists the removal AND drops the queue entry`() {
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()
        assertThat(sut.alarmQueue.value).hasSize(1)

        sut.acknowledgeAndRemoveAlarm("alarm-1")

        // Without the persist a "cleared" alarm resurrects on the next cold load.
        verifyBlocking(alarmUseCase) { acknowledgeAlarm(eq("alarm-1")) }
        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `acknowledgeAndRemoveAlarm still drops the queue entry when the persist fails`() {
        whenever { alarmUseCase.acknowledgeAlarm(any()) }.thenAnswer { throw IllegalStateException("boom") }
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()

        sut.acknowledgeAndRemoveAlarm("alarm-1")

        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `clearAllAlarms persists the wipe and empties the queue`() {
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm(), alarm(id = "alarm-2")))
        sut.loadActiveAlarms()

        sut.clearAllAlarms()

        verifyBlocking(alarmUseCase) { clearAlarms() }
        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `confirmed clearable alert is cleared on the patch then acknowledged`() {
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(true)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(alarm()))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator) { clearAlarmOnPatch(any()) }
        verifyBlocking(alarmUseCase) { acknowledgeAlarm(eq("alarm-1")) }
    }

    @Test
    fun `failed patch clear falls back to the local acknowledge path`() {
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(false)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(alarm()))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmUseCase) { acknowledgeAlarm(eq("alarm-1")) }
        verify(alarmClearCoordinator).forgetSnapshotAlarmCause(alarm().cause)
    }

    @Test
    fun `confirmed patch clear keeps the snapshot baseline`() {
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(true)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(alarm()))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verify(alarmClearCoordinator, never()).forgetSnapshotAlarmCause(any())
    }

    @Test
    fun `BLE not connected alert is acknowledged locally and keeps the snapshot baseline`() {
        val bleAlert = alarm(id = "ble", cause = AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(bleAlert))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(bleAlert))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmUseCase) { acknowledgeAlarm(eq("ble")) }
        verify(alarmClearCoordinator, never()).forgetSnapshotAlarmCause(any())
    }

    @Test
    fun `informational notice is acknowledged locally without any patch operation`() {
        val notice = alarm(id = "lgs", cause = AlarmCause.ALARM_NOTICE_LGS_START)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(notice))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(notice))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator, never()) { clearAlarmOnPatch(any()) }
        verifyBlocking(alarmClearCoordinator, never()) { discardOnAlarm(any()) }
        verifyBlocking(alarmUseCase) { acknowledgeAlarm(eq("lgs")) }
    }

    @Test
    fun `warning-tier alarm routes to the patch discard flow`() {
        whenever(alarmClearCoordinator.isPatchReachable()).thenReturn(true)
        whenever { alarmClearCoordinator.discardOnAlarm(any()) }.thenReturn(true)
        // The real coordinator runs the completion callback after unbond+flush; mirror that so the
        // handler's queue-clearing continuation executes.
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as () -> Unit).invoke()
        }.whenever(alarmClearCoordinator).forceQuitTeardown(any())
        val warning = alarm(id = "clog", cause = AlarmCause.ALARM_WARNING_PUMP_CLOGGED)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(warning))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(warning))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator) { discardOnAlarm(any()) }
        verify(alarmClearCoordinator).forceQuitTeardown(any())
    }

    /** Collects the UI requests from now on, so a request sent on another thread is not missed. */
    private fun collectUiRequests(): Pair<List<AlarmEvent>, Job> {
        val requests = CopyOnWriteArrayList<AlarmEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { sut.uiRequests.collect { requests += it } }
        return requests to job
    }

    private fun runTeardownCallback() {
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as () -> Unit).invoke()
        }.whenever(alarmClearCoordinator).forceQuitTeardown(any())
    }

    private fun statusRead(success: Boolean): PumpEnactResult = mock { on { this.success } doReturn success }

    @Test
    fun `a delivery resume timeout cleared on the patch resumes the delivery`() {
        val timeout = alarm(id = "resume", cause = AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT)
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(true)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(timeout))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(timeout))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmUseCase) { acknowledgeAlarm(eq("resume")) }
        verifyBlocking(alarmClearCoordinator, timeout(5_000)) { resumeInfusion() }
    }

    @Test
    fun `an app auto off warning is cleared and the delivery resumed, not the patch discarded`() {
        val autoOff = alarm(id = "auto-off", cause = AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF)
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(true)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(autoOff))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(autoOff))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator, timeout(5_000)) { resumeInfusion() }
        verifyBlocking(alarmClearCoordinator, never()) { discardOnAlarm(any()) }
    }

    @Test
    fun `a delivery resume timeout the patch did not clear keeps the alarm and resumes nothing`() {
        val timeout = alarm(id = "resume", cause = AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT)
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(false)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(timeout))
        sut.loadActiveAlarms()
        val (requests, job) = collectUiRequests()

        sut.triggerEvent(AlarmEvent.ClearAlarm(timeout))

        awaitCondition { requests.isNotEmpty() }
        job.cancel()
        assertThat(requests).containsExactly(AlarmEvent.ShowToastMessage(R.string.alarm_feat_msg_check_patch_connect))
        assertThat(sut.alarmQueue.value.map { it.alarmId }).containsExactly("resume")
        verifyBlocking(alarmUseCase, never()) { acknowledgeAlarm(any()) }
        verifyBlocking(alarmClearCoordinator, never()) { resumeInfusion() }
    }

    @Test
    fun `a warning with the patch out of reach is abandoned at once without a discard command`() {
        whenever(alarmClearCoordinator.isPatchReachable()).thenReturn(false)
        runTeardownCallback()
        val warning = alarm(id = "clog", cause = AlarmCause.ALARM_WARNING_PUMP_CLOGGED)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(warning))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(warning))

        // No wait: the teardown runs on the caller's thread.
        assertThat(sut.alarmQueue.value).isEmpty()
        verify(alarmClearCoordinator).forceQuitTeardown(any())
        verifyBlocking(alarmUseCase) { clearAlarms() }
        verifyBlocking(alarmClearCoordinator, never()) { discardOnAlarm(any()) }
    }

    @Test
    fun `an unknown cause of the warning tier discards the patch`() {
        whenever(alarmClearCoordinator.isPatchReachable()).thenReturn(false)
        runTeardownCallback()
        val unknown = alarm(id = "unknown", cause = AlarmCause.ALARM_UNKNOWN).copy(alarmType = AlarmType.WARNING)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(unknown))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(unknown))

        verify(alarmClearCoordinator).forceQuitTeardown(any())
        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `an unknown cause of another tier does nothing`() {
        val unknown = alarm(id = "unknown", cause = AlarmCause.ALARM_UNKNOWN)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(unknown))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(unknown))

        assertThat(sut.alarmQueue.value.map { it.alarmId }).containsExactly("unknown")
        verifyBlocking(alarmUseCase, never()) { acknowledgeAlarm(any()) }
        verify(alarmClearCoordinator, never()).forceQuitTeardown(any())
        assertThat(sut.alarmInfo).isEqualTo(unknown)
    }

    @Test
    fun `Bluetooth off asks for Bluetooth and keeps the alarm until the queue reaches the patch`() {
        val btOff = alarm(id = "bt-off", cause = AlarmCause.ALARM_ALERT_BLUETOOTH_OFF)
        val readDone = CompletableDeferred<Unit>()
        val reached = statusRead(true)
        // The read suspends (it does not block): the handler runs on the test thread now, and a blocking
        // read would hold that thread, so readDone below could never be completed.
        whenever { commandQueue.readStatus(any()) } doSuspendableAnswer {
            readDone.await()
            reached
        }
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(btOff))
        sut.loadActiveAlarms()
        val (requests, job) = collectUiRequests()

        sut.triggerEvent(AlarmEvent.ClearAlarm(btOff))

        awaitCondition { requests.isNotEmpty() }
        assertThat(requests).containsExactly(AlarmEvent.RequestBluetoothEnable)
        assertThat(sut.alarmQueue.value.map { it.alarmId }).containsExactly("bt-off")
        verify(alarmClearCoordinator, never()).forgetSnapshotAlarmCause(any())

        readDone.complete(Unit)

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        job.cancel()
        verifyBlocking(commandQueue) { readStatus("Bluetooth re-enabled after alarm") }
        verifyBlocking(alarmUseCase, times(2)) { acknowledgeAlarm(eq("bt-off")) }
    }

    @Test
    fun `Bluetooth off keeps the alarm when the queue cannot reach the patch`() {
        val btOff = alarm(id = "bt-off", cause = AlarmCause.ALARM_ALERT_BLUETOOTH_OFF)
        val notReached = statusRead(false)
        whenever { commandQueue.readStatus(any()) }.thenReturn(notReached)
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(btOff))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(btOff))

        verifyBlocking(commandQueue, timeout(5_000)) { readStatus(any()) }
        verify(notReached, timeout(5_000)).success
        assertThat(sut.alarmQueue.value.map { it.alarmId }).containsExactly("bt-off")
        verifyBlocking(alarmUseCase, times(1)) { acknowledgeAlarm(eq("bt-off")) }
    }

    @Test
    fun `a failed wipe of all alarms keeps the queue`() {
        whenever { alarmUseCase.clearAlarms() }.thenAnswer { throw IllegalStateException("boom") }
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()

        sut.clearAllAlarms()

        assertThat(sut.alarmQueue.value).hasSize(1)
    }

    @Test
    fun `an emptied queue is announced once per emptying`() {
        whenever { alarmUseCase.getAlarmsOnce() }.thenReturn(listOf(alarm()))
        sut.loadActiveAlarms()
        val events = CopyOnWriteArrayList<Unit>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { sut.alarmQueueEmptyEvent.collect { events += it } }

        sut.acknowledgeAndRemoveAlarm("alarm-1")
        sut.clearAllAlarms()

        job.cancel()
        assertThat(events).hasSize(2)
    }

    @Test
    fun `observed alarms are a plain list, empty when nothing is stored`() {
        // The store's "nothing stored" (null) becomes an empty list in the repository now, so the use case gives an empty list.
        whenever(alarmUseCase.observeAlarms()).thenReturn(flowOf(emptyList(), listOf(alarm())))

        val lists = runBlocking { sut.observeAlarms().toList() }

        assertThat(lists.map { list -> list.map { it.alarmId } }).containsExactly(emptyList<String>(), listOf("alarm-1")).inOrder()
    }

    @Test
    fun `an event that is not a clear does nothing`() {
        sut.triggerEvent(AlarmEvent.NoAction)

        assertThat(sut.alarmInfo).isNull()
        verifyBlocking(alarmUseCase, never()) { acknowledgeAlarm(any()) }
    }
}

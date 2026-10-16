package app.aaps.pump.carelevo.common

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.coordinator.CarelevoAlarmClearCoordinator
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.usecase.alarm.CarelevoAlarmInfoUseCase
import app.aaps.pump.carelevo.presentation.model.AlarmEvent
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.delay
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoAlarmActionHandlerTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var aapsSchedulers: AapsSchedulers
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
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        whenever(aapsSchedulers.main).thenReturn(Schedulers.trampoline())
        whenever(alarmUseCase.acknowledgeAlarm(any())).thenReturn(Completable.complete())
        whenever(alarmUseCase.clearAlarms()).thenReturn(Completable.complete())
        sut = CarelevoAlarmActionHandler(aapsLogger, aapsSchedulers, commandQueue, alarmUseCase, alarmClearCoordinator)
    }

    /** Await an asynchronous (Dispatchers.IO-launched) state change with a bounded timeout. */
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
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(notice, warningLate, warningEarly))))

        sut.loadActiveAlarms()

        assertThat(sut.alarmQueue.value.map { it.alarmId })
            .containsExactly("warning-early", "warning-late", "notice")
            .inOrder()
    }

    @Test
    fun `acknowledgeAndRemoveAlarm persists the removal AND drops the queue entry`() {
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(alarm()))))
        sut.loadActiveAlarms()
        assertThat(sut.alarmQueue.value).hasSize(1)

        sut.acknowledgeAndRemoveAlarm("alarm-1")

        // Without the persist a "cleared" alarm resurrects on the next cold load.
        verify(alarmUseCase).acknowledgeAlarm(eq("alarm-1"))
        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `acknowledgeAndRemoveAlarm still drops the queue entry when the persist fails`() {
        whenever(alarmUseCase.acknowledgeAlarm(any())).thenReturn(Completable.error(IllegalStateException("boom")))
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(alarm()))))
        sut.loadActiveAlarms()

        sut.acknowledgeAndRemoveAlarm("alarm-1")

        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `clearAllAlarms persists the wipe and empties the queue`() {
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(alarm(), alarm(id = "alarm-2")))))
        sut.loadActiveAlarms()

        sut.clearAllAlarms()

        verify(alarmUseCase).clearAlarms()
        assertThat(sut.alarmQueue.value).isEmpty()
    }

    @Test
    fun `confirmed clearable alert is cleared on the patch then acknowledged`() {
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(true)
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(alarm()))))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(alarm()))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator) { clearAlarmOnPatch(any()) }
        verify(alarmUseCase).acknowledgeAlarm(eq("alarm-1"))
    }

    @Test
    fun `failed patch clear falls back to the local acknowledge path`() {
        whenever { alarmClearCoordinator.clearAlarmOnPatch(any()) }.thenReturn(false)
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(alarm()))))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(alarm()))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verify(alarmUseCase).acknowledgeAlarm(eq("alarm-1"))
    }

    @Test
    fun `informational notice is acknowledged locally without any patch operation`() {
        val notice = alarm(id = "lgs", cause = AlarmCause.ALARM_NOTICE_LGS_START)
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(notice))))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(notice))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator, never()) { clearAlarmOnPatch(any()) }
        verifyBlocking(alarmClearCoordinator, never()) { discardOnAlarm(any()) }
        verify(alarmUseCase).acknowledgeAlarm(eq("lgs"))
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
        whenever(alarmUseCase.getAlarmsOnce()).thenReturn(Single.just(Optional.of(listOf(warning))))
        sut.loadActiveAlarms()

        sut.triggerEvent(AlarmEvent.ClearAlarm(warning))

        awaitCondition { sut.alarmQueue.value.isEmpty() }
        verifyBlocking(alarmClearCoordinator) { discardOnAlarm(any()) }
        verify(alarmClearCoordinator).forceQuitTeardown(any())
    }
}

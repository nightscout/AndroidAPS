package app.aaps.pump.carelevo.coordinator

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.carelevo.ble.CarelevoBleTransport
import app.aaps.pump.carelevo.command.CmdAlarmClear
import app.aaps.pump.carelevo.common.CarelevoPatch
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoAlarmClearCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var carelevoPatch: CarelevoPatch
    @Mock lateinit var transport: CarelevoBleTransport
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: CarelevoAlarmClearCoordinator

    private fun enactResult(success: Boolean): PumpEnactResult = mock<PumpEnactResult>().also {
        whenever(it.success).thenReturn(success)
    }

    private fun alarm(cause: AlarmCause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = "alarm-1",
            alarmType = cause.alarmType,
            cause = cause,
            value = null,
            createdAt = "2026-07-16T10:00:00",
            updatedAt = "2026-07-16T10:00:00",
            isAcknowledged = false
        )

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(1_000_000L)
        whenever(carelevoPatch.patchInfo).thenReturn(BehaviorSubject.createDefault(Optional.empty<CarelevoPatchInfoDomainModel>()))
        sut = CarelevoAlarmClearCoordinator(aapsLogger, commandQueue, carelevoPatch, transport, pumpSync, dateUtil)
    }

    @Test
    fun `clearAlarmOnPatch returns the queue result`() = runBlocking {
        // Build the result mocks BEFORE stubbing — creating a stubbed mock inside thenReturn is
        // "stubbing another mock before thenReturn completes" and trips UnfinishedStubbingException.
        val ok = enactResult(true)
        val fail = enactResult(false)

        whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
        assertThat(sut.clearAlarmOnPatch(alarm())).isTrue()

        whenever { commandQueue.customCommand(any()) }.thenReturn(fail)
        assertThat(sut.clearAlarmOnPatch(alarm())).isFalse()
    }

    @Test
    fun `opMutex serializes two concurrent clear operations`(): Unit = runBlocking {
        // First command call parks until released; a concurrent second clear must NOT reach the
        // queue while the first holds the mutex — CommandQueue dedups by command class, so a
        // parallel second alarm-clear would misread as a clear-failure and locally dismiss an
        // alarm that was never cleared on the patch.
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)
        val ok = enactResult(true)
        whenever { commandQueue.customCommand(any()) }.thenAnswer {
            if (callCount.incrementAndGet() == 1) {
                firstStarted.complete(Unit)
                runBlocking { releaseFirst.await() }
            }
            ok
        }

        val first = async(Dispatchers.Default) { sut.clearAlarmOnPatch(alarm()) }
        firstStarted.await()
        val second = async(Dispatchers.Default) { sut.discardOnAlarm(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED)) }
        // Give the second op every chance to (incorrectly) enter the queue while the first holds the mutex.
        delay(300)
        assertThat(callCount.get()).isEqualTo(1)

        releaseFirst.complete(Unit)
        assertThat(first.await()).isTrue()
        assertThat(second.await()).isTrue()
        assertThat(callCount.get()).isEqualTo(2)
    }

    @Test
    fun `resumeInfusion does not record a TBR stop when none is expected to be running`() = runBlocking {
        val ok = enactResult(true)
        whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
        whenever { pumpSync.expectedPumpState() }.thenReturn(
            PumpSync.PumpState(temporaryBasal = null, extendedBolus = null, bolus = null, profile = null, serialNumber = "SN")
        )

        assertThat(sut.resumeInfusion()).isTrue()
        verifyBlocking(pumpSync, never()) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `resumeInfusion records the TBR stop when one is running`() = runBlocking {
        val ok = enactResult(true)
        whenever { commandQueue.customCommand(any()) }.thenReturn(ok)
        val tbr = PumpSync.PumpState.TemporaryBasal(
            timestamp = 0L, duration = 60_000L, rate = 0.0, isAbsolute = true,
            type = PumpSync.TemporaryBasalType.PUMP_SUSPEND, id = 1L, pumpId = 0L,
            pumpType = PumpType.CAREMEDI_CARELEVO, pumpSerial = "SN"
        )
        whenever { pumpSync.expectedPumpState() }.thenReturn(
            PumpSync.PumpState(temporaryBasal = tbr, extendedBolus = null, bolus = null, profile = null, serialNumber = "SN")
        )

        assertThat(sut.resumeInfusion()).isTrue()
        verifyBlocking(pumpSync) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `resumeInfusion does not touch pumpSync when the resume command fails`() = runBlocking {
        val fail = enactResult(false)
        whenever { commandQueue.customCommand(any()) }.thenReturn(fail)

        assertThat(sut.resumeInfusion()).isFalse()
        verifyBlocking(pumpSync, never()) { syncStopTemporaryBasalWithPumpId(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `forceQuitTeardown flushes state and completes even when the unbond throws`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn("aa:bb:cc:dd:ee:ff")
        whenever(transport.adapter).thenThrow(RuntimeException("no adapter"))
        var completed = false

        sut.forceQuitTeardown { completed = true }

        assertThat(completed).isTrue()
        verify(carelevoPatch).flushPatchInformation()
    }

    @Test
    fun `isPatchReachable requires both a stored address and enabled bluetooth`() {
        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn(null)
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        assertThat(sut.isPatchReachable()).isFalse()

        whenever(carelevoPatch.getPatchInfoAddress()).thenReturn("aa:bb:cc:dd:ee:ff")
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)
        assertThat(sut.isPatchReachable()).isFalse()

        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        assertThat(sut.isPatchReachable()).isTrue()
    }

    @Test
    fun `clearAlarmOnPatch routes through the queue as a custom command`() = runBlocking {
        val ok = enactResult(true)
        whenever { commandQueue.customCommand(any()) }.thenReturn(ok)

        sut.clearAlarmOnPatch(alarm())

        verifyBlocking(commandQueue) { customCommand(any<CmdAlarmClear>()) }
    }
}

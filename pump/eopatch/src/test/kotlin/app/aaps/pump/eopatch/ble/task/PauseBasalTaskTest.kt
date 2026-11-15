package app.aaps.pump.eopatch.ble.task

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.core.api.BasalPause
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * A suspend from the patch screen during an SMB failed: the SMB was not stopped, and the pause waited for it and for
 * the temp basal cancel within a 10 s limit. A second suspend did not wait for the cancel at all, because "ready"
 * stayed `true` in subjects of this singleton task.
 */
class PauseBasalTaskTest {

    private val commandQueue: CommandQueue = mock()
    private val pumpSync: PumpSync = mock()
    private val basalPause: BasalPause = mock()

    private lateinit var sut: PauseBasalTask

    @BeforeEach
    fun setUp() {
        sut = PauseBasalTask(mock<IAlarmRegistry>(), commandQueue, pumpSync, mock<UserEntryLogger>(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        sut.aapsLogger = mock<AAPSLogger>()
        sut.pm = mock<PreferenceManager>().also { pm ->
            whenever(pm.patchState).thenReturn(mock<PatchState>())   // not paused yet
        }
        // Already suspended in the driver's eyes: onBasalPaused then has nothing more to do.
        sut.normalBasalManager = mock<NormalBasalManager>().also { whenever(it.isSuspended()).thenReturn(true) }
        sut.patchConfig = mock<PatchConfig>()
        sut.taskQueue = mock<TaskQueue>().also { whenever(it.isReady(any())).thenReturn(Observable.just(TaskFunc.PAUSE_BASAL)) }
        sut.basalPause = basalPause
        whenever(basalPause.pause(any())).thenReturn(Single.just(PatchBooleanResponse(true)))
        runBlocking {
            whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(mock<PumpEnactResult>())
            whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(null, null, null, null, "JP100019950"))
        }
    }

    private fun aTempBasalRuns() = runBlocking {
        val tbr = PumpSync.PumpState.TemporaryBasal(
            timestamp = 0L, duration = 30 * 60_000L, rate = 1.65, isAbsolute = true, type = PumpSync.TemporaryBasalType.NORMAL,
            id = 1L, pumpId = 1L, pumpType = PumpType.EOFLOW_EOPATCH2, pumpSerial = "JP100019950"
        )
        whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(tbr, null, null, null, "JP100019950"))
    }

    @Test
    fun aRunningSmbIsStoppedBeforeThePause() {
        whenever(commandQueue.isRunning(Command.CommandType.SMB_BOLUS)).thenReturn(true)

        sut.pause(0.5f, null).blockingGet()

        val order = inOrder(commandQueue, basalPause)
        order.verify(commandQueue).cancelAllBoluses(anyOrNull())
        order.verify(basalPause).pause(0.5f)
    }

    @Test
    fun everyPauseWaitsUntilTheTempBasalIsCancelled(): Unit = runBlocking {
        aTempBasalRuns()

        sut.pause(0.5f, null).blockingGet()
        sut.pause(0.5f, null).blockingGet()

        // Cancel, pause, cancel, pause. The second pause used to go first.
        val order = inOrder(commandQueue, basalPause)
        repeat(2) {
            order.verify(commandQueue).cancelTempBasal(any(), any())
            order.verify(basalPause).pause(0.5f)
        }
        verify(basalPause, times(2)).pause(0.5f)
    }
}

package app.aaps.plugins.aps.loop.runningMode

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RunningModeExpiryWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var workerParameters: WorkerParameters

    private lateinit var worker: RunningModeExpiryWorker

    @BeforeEach
    fun setup() {
        worker = RunningModeExpiryWorker(
            context, workerParameters, aapsLogger, fabricPrivacy,
            processedTbrEbData, commandQueue, dateUtil, config
        )
    }

    @Test
    fun `skips when config APS is false`() = runTest {
        whenever(config.APS).thenReturn(false)

        val result = worker.doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(processedTbrEbData, never()).getTempBasalIncludingConvertedExtended(any())
        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }

    @Test
    fun `cancels EMULATED_PUMP_SUSPEND TBR at expiry`() = runTest {
        whenever(config.APS).thenReturn(true)
        val tbr = mock<TB> { on { type } doReturn TB.Type.EMULATED_PUMP_SUSPEND }
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(tbr)
        whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(mock<PumpEnactResult>())

        val result = worker.doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(commandQueue).cancelTempBasal(eq(true), eq(false))
    }

    @Test
    fun `no-op when no TBR active`() = runTest {
        whenever(config.APS).thenReturn(true)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(null)

        val result = worker.doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }

    @Test
    fun `no-op when TBR is not EMULATED_PUMP_SUSPEND`() = runTest {
        whenever(config.APS).thenReturn(true)
        val tbr = mock<TB> { on { type } doReturn TB.Type.NORMAL }
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(tbr)

        val result = worker.doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }
}

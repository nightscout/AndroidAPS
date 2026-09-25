package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
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

/**
 * These cases used to live in `RunningModeExpiryWorkerTest` and needed a `WorkerParameters` mock and a
 * `ListenableWorker.Result` assertion to reach four lines of decision.
 *
 * The decision moved to [RunningModeExpiryJob], so the test is now about the behaviour and nothing
 * else - no WorkManager types appear. The worker left behind in :app is a three line envelope with
 * nothing to assert. That is the point of the split, and it is what a second platform inherits.
 */
class RunningModeExpiryJobTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue

    private lateinit var sut: RunningModeExpiryJob

    @BeforeEach
    fun setup() {
        sut = RunningModeExpiryJob(
            aapsLogger = aapsLogger,
            config = config,
            dateUtil = dateUtil,
            processedTbrEbData = processedTbrEbData,
            commandQueue = commandQueue
        )
    }

    @Test
    fun `skips when config APS is false`() = runTest {
        whenever(config.APS).thenReturn(false)

        sut.run()

        verify(processedTbrEbData, never()).getTempBasalIncludingConvertedExtended(any())
        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }

    @Test
    fun `cancels EMULATED_PUMP_SUSPEND TBR at expiry`() = runTest {
        whenever(config.APS).thenReturn(true)
        val tbr = mock<TB> { on { type } doReturn TB.Type.EMULATED_PUMP_SUSPEND }
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(tbr)
        whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(mock<PumpEnactResult>())

        sut.run()

        // enforceNew = true: the zero-TBR must go even if the pump reports one already running.
        verify(commandQueue).cancelTempBasal(eq(true), eq(false))
    }

    @Test
    fun `no-op when no TBR active`() = runTest {
        whenever(config.APS).thenReturn(true)
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(null)

        sut.run()

        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }

    @Test
    fun `no-op when TBR is not EMULATED_PUMP_SUSPEND`() = runTest {
        whenever(config.APS).thenReturn(true)
        val tbr = mock<TB> { on { type } doReturn TB.Type.NORMAL }
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(tbr)

        sut.run()

        verify(commandQueue, never()).cancelTempBasal(any(), any())
    }
}

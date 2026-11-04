package app.aaps.pump.aaruy.services

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `SystemClock` is virtual under Robolectric, so the long waits of the bolus do not take real time. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyServiceBolusTest {

    private val preferences = mock(Preferences::class.java, RETURNS_SMART_NULLS)
    private val pumpSync = mock(PumpSync::class.java)
    private val dateUtil = mock(DateUtil::class.java)
    private val ble = mock(AaruyBLE::class.java)
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java, RETURNS_SMART_NULLS),
        preferences,
        dateUtil,
        pumpSync,
        mock(TemporaryBasalStorage::class.java),
        mock(DecimalFormatter::class.java)
    )
    private val service = AaruyService()
    private val info = DetailedBolusInfo().apply { insulin = 0.5 }

    @Before
    fun setUp() {
        BolusProgressData.stopPressed = false
        BolusProgressData.delivered = 0.0
        `when`(dateUtil.now()).thenAnswer { SystemClock.elapsedRealtime() }
        service.dateUtil = dateUtil
        service.aapsLogger = mock(AAPSLogger::class.java)
        service.rxBus = mock(RxBus::class.java)
        service.rh = mock(ResourceHelper::class.java, RETURNS_SMART_NULLS)
        service.preferences = preferences
        service.commandQueue = mock(CommandQueue::class.java)
        service.aaruyPump = pump
        service.aaruyBle = ble
        service.pumpSync = pumpSync
        service.detailedBolusInfoStorage = mock(DetailedBolusInfoStorage::class.java)
        service.isConnected = true
        pump.pumpState = AaruyPumpState.BASALRATE_RUNNING
        pump.bolusStep = 2
    }

    private fun pumpAnswers(action: () -> Unit) {
        `when`(ble.setBolus(anyDouble(), anyBoolean())).thenAnswer {
            action()
            true
        }
    }

    @Test
    fun finishedBolusIsFinished() {
        pumpAnswers { pump.handleBolusStatusUpdate(0, true, 0.5) }
        assertEquals(AaruyBolusOutcome.FINISHED, service.setBolus(info))
        assertEquals(0.5, BolusProgressData.delivered, 0.0001)
    }

    @Test
    fun silenceAfterTheStartIsNotAFinishedBolus() {
        pumpAnswers { pump.handleBolusStatusUpdate(0, false, 0.1) }
        assertEquals(AaruyBolusOutcome.UNKNOWN, service.setBolus(info))
        assertEquals(0.1, BolusProgressData.delivered, 0.0001)
        assertFalse("the next bolus must wait for the real state", pump.bolusDone)
    }

    @Test
    fun aBolusEndThatWasNotSeenIsNotAFinishedBolus() {
        pumpAnswers {
            pump.handleBolusStatusUpdate(0, false, 0.1)
            pump.handleBolusEndNotSeen()
        }
        assertEquals(AaruyBolusOutcome.UNKNOWN, service.setBolus(info))
        assertTrue(pump.bolusDone)
    }

    @Test
    fun theNextBolusForgetsTheEndThatWasNotSeen() {
        pump.handleBolusEndNotSeen()
        pumpAnswers { pump.handleBolusStatusUpdate(0, true, 0.5) }
        assertEquals(AaruyBolusOutcome.FINISHED, service.setBolus(info))
    }

    @Test
    fun aBolusThePumpDidNotStartIsNotStarted() {
        `when`(ble.setBolus(anyDouble(), anyBoolean())).thenReturn(false)
        assertEquals(AaruyBolusOutcome.NOT_STARTED, service.setBolus(info))
        assertTrue(pump.bolusDone)
    }

    @Test
    fun noNewBolusWhileTheLastOneIsNotKnownToBeOver() {
        pump.bolusDone = false
        assertEquals(AaruyBolusOutcome.NOT_STARTED, service.setBolus(info))
        verify(ble, never()).setBolus(anyDouble(), anyBoolean())
    }

    @Test
    fun whenConnectedAndSilentItAsksForTheStatus() {
        pumpAnswers { pump.handleBolusStatusUpdate(0, false, 0.1) }
        var asked = 0
        `when`(ble.syncPumpStatus()).thenAnswer {
            // The answer counts as progress, and the bolus ends after a few of them.
            asked++
            pump.bolusProgressLastTimeStamp = dateUtil.now() + 1
            if (asked == 5) pump.handleBolusStatusUpdate(0, true, 0.5)
            Unit
        }
        assertEquals(AaruyBolusOutcome.FINISHED, service.setBolus(info))
        assertTrue("more than the 3 retries, because each answer is progress: $asked", asked >= 5)
    }

    @Test
    fun aStopThePumpDidNotConfirmStopsNothing() {
        `when`(ble.cancelBolus()).thenAnswer {
            SystemClock.sleep(500)
            false
        }
        service.stopBolus()
        assertFalse(pump.bolusStopped)
    }

    @Test
    fun aConfirmedStopStops() {
        `when`(ble.cancelBolus()).thenReturn(true)
        service.stopBolus()
        assertTrue(pump.bolusStopped)
    }
}

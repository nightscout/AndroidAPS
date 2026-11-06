package app.aaps.pump.aaruy.services

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyServiceTempBasalTest {

    private val pumpSync = mock(PumpSync::class.java)
    private val ble = mock(AaruyBLE::class.java)
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java, RETURNS_SMART_NULLS),
        mock(Preferences::class.java, RETURNS_SMART_NULLS),
        mock(DateUtil::class.java),
        pumpSync,
        mock(TemporaryBasalStorage::class.java),
        mock(BolusProgressData::class.java)
    )
    private val service = AaruyService()

    @Before
    fun setUp() {
        service.aapsLogger = mock(AAPSLogger::class.java)
        service.aaruyPump = pump
        service.aaruyBle = ble
        service.pumpSync = pumpSync
        service.isConnected = true
        `when`(ble.cancelTempBasal()).thenReturn(true)
        `when`(ble.setTempBasal(anyDouble(), anyInt())).thenReturn(true)
        runBlocking { `when`(pumpSync.expectedPumpState()).thenReturn(mock(PumpSync.PumpState::class.java)) }
    }

    private fun setTempBasal() = runBlocking { service.setTempBasal(1.0, 30) }

    @Test
    fun aRunningTempBasalIsChanged() {
        pump.pumpState = AaruyPumpState.TMPBASALRATE_RUNNING

        assertTrue(setTempBasal())

        val order = inOrder(ble)
        order.verify(ble).cancelTempBasal()
        order.verify(ble).setTempBasal(1.0, 30)
    }

    @Test
    fun fromThePlainBasalNothingIsCancelled() {
        pump.pumpState = AaruyPumpState.BASALRATE_RUNNING

        assertTrue(setTempBasal())

        verify(ble, never()).cancelTempBasal()
        verify(ble).setTempBasal(1.0, 30)
    }

    @Test
    fun noNewTempBasalWhenTheCancelGotNoAnswer() {
        pump.pumpState = AaruyPumpState.TMPBASALRATE_RUNNING
        `when`(ble.cancelTempBasal()).thenReturn(false)

        assertFalse(setTempBasal())

        verify(ble, never()).setTempBasal(anyDouble(), anyInt())
    }

    /** The pump ended it during a lost link. The client sends nothing then, and every cancel of the loop failed after 5 s. */
    @Test
    fun aCancelWhenThePumpRunsNoTempBasalIsDone() {
        for (state in listOf(AaruyPumpState.BASALRATE_RUNNING, AaruyPumpState.STOP_RUNNING)) {
            pump.pumpState = state
            assertTrue(state.name, runBlocking { service.cancelTempBasal() })
        }
        verify(ble, never()).cancelTempBasal()
    }

    @Test
    fun noTempBasalDuringABolusOrAStop() {
        for (state in listOf(AaruyPumpState.LARGEDOSE_RUNNING, AaruyPumpState.STOP_RUNNING, AaruyPumpState.PAUSE_RUNNING, AaruyPumpState.FIRST_INIT)) {
            pump.pumpState = state
            assertFalse(state.name, setTempBasal())
        }
        verify(ble, never()).cancelTempBasal()
        verify(ble, never()).setTempBasal(anyDouble(), anyInt())
    }
}

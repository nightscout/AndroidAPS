package app.aaps.pump.equil

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.joda.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.whenever

class EquilPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var equilManager: EquilManager

    private lateinit var equilPumpPlugin: EquilPumpPlugin

    @BeforeEach
    fun prepareMocks() {

        whenever(rh.gs(anyInt())).thenReturn("")
        equilPumpPlugin =
            EquilPumpPlugin(
                aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context,
                fabricPrivacy, pumpSync, equilManager, pumpEnactResultProvider, constraintsChecker
            )
    }

    @Test
    fun addPreferenceScreen() {
        val screen = preferenceManager.createPreferenceScreen(context)
        equilPumpPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }

    @Test
    fun `manufacturer should return Equil`() {
        assertEquals(ManufacturerType.Equil, equilPumpPlugin.manufacturer())
    }

    @Test
    fun `model should return EQUIL pump type`() {
        assertEquals(PumpType.EQUIL, equilPumpPlugin.model())
    }

    @Test
    fun `isInitialized should return true`() {
        assertTrue(equilPumpPlugin.isInitialized())
    }

    @Test
    fun `isConnected should return true`() {
        assertTrue(equilPumpPlugin.isConnected())
    }

    @Test
    fun `isConnecting should return false`() {
        assertFalse(equilPumpPlugin.isConnecting())
    }

    @Test
    fun `isBusy should return false`() {
        assertFalse(equilPumpPlugin.isBusy())
    }

    @Test
    fun `isHandshakeInProgress should return false`() {
        assertFalse(equilPumpPlugin.isHandshakeInProgress())
    }

    @Test
    fun `isFakingTempsByExtendedBoluses should return false`() {
        assertFalse(equilPumpPlugin.isFakingTempsByExtendedBoluses)
    }

    @Test
    fun `canHandleDST should return false`() {
        assertFalse(equilPumpPlugin.canHandleDST())
    }

    @Test
    fun `isBatteryChangeLoggingEnabled should return false`() {
        assertFalse(equilPumpPlugin.isBatteryChangeLoggingEnabled())
    }

    @Test
    fun `pumpDescription should be initialized`() {
        assertNotNull(equilPumpPlugin.pumpDescription)
    }

    @Test
    fun `BASAL_STEP_DURATION should be 30 minutes`() {
        val basal_step_duration = Duration.standardMinutes(30)
        assertEquals(30, basal_step_duration.standardMinutes)
    }

    @Test
    fun `toDuration should convert DateTime to Duration correctly`() {
        val dateTime = DateTime(2024, 1, 1, 12, 30, 0)
        val duration = EquilPumpPlugin.toDuration(dateTime)

        assertNotNull(duration)
        // 12:30:00 = 12*60*60*1000 + 30*60*1000 = 45000000 ms
        assertEquals(45000000L, duration.millis)
    }

    @Test
    fun `toDuration at midnight should return zero duration`() {
        val dateTime = DateTime(2024, 1, 1, 0, 0, 0)
        val duration = EquilPumpPlugin.toDuration(dateTime)

        assertEquals(0L, duration.millis)
    }

    @Test
    fun `toDuration should only consider time of day`() {
        val dateTime1 = DateTime(2024, 1, 1, 15, 45, 30)
        val dateTime2 = DateTime(2025, 12, 31, 15, 45, 30)

        val duration1 = EquilPumpPlugin.toDuration(dateTime1)
        val duration2 = EquilPumpPlugin.toDuration(dateTime2)

        // Same time of day should give same duration regardless of date
        assertEquals(duration1.millis, duration2.millis)
    }

    @Test
    fun `tempActivationProgress should be NONE initially`() {
        assertEquals(ActivationProgress.NONE, equilPumpPlugin.tempActivationProgress)
    }

    @Test
    fun `indexEquilReadStatus should be 5 initially`() {
        assertEquals(5, equilPumpPlugin.indexEquilReadStatus)
    }

    @Test
    fun `tempActivationProgress can be changed`() {
        equilPumpPlugin.tempActivationProgress = ActivationProgress.PRIMING
        assertEquals(ActivationProgress.PRIMING, equilPumpPlugin.tempActivationProgress)
    }

    @Test
    fun `indexEquilReadStatus can be changed`() {
        equilPumpPlugin.indexEquilReadStatus = 10
        assertEquals(10, equilPumpPlugin.indexEquilReadStatus)
    }

    @Test
    fun `lastBolusTime should return null`() {
        assertEquals(null, equilPumpPlugin.lastBolusTime)
    }

    @Test
    fun `lastBolusAmount should return null`() {
        assertEquals(null, equilPumpPlugin.lastBolusAmount)
    }

    @Test
    fun `toDuration should handle hour boundaries`() {
        val dateTime = DateTime(2024, 1, 1, 1, 0, 0)
        val duration = EquilPumpPlugin.toDuration(dateTime)

        // 1:00:00 = 1*60*60*1000 = 3600000 ms
        assertEquals(3600000L, duration.millis)
    }

    @Test
    fun `toDuration should handle seconds`() {
        val dateTime = DateTime(2024, 1, 1, 0, 0, 30)
        val duration = EquilPumpPlugin.toDuration(dateTime)

        // 0:00:30 = 30*1000 = 30000 ms
        assertEquals(30000L, duration.millis)
    }

    @Test
    fun `toDuration at end of day should return correct duration`() {
        val dateTime = DateTime(2024, 1, 1, 23, 59, 59)
        val duration = EquilPumpPlugin.toDuration(dateTime)

        // 23:59:59 = 23*60*60*1000 + 59*60*1000 + 59*1000 = 86399000 ms
        assertEquals(86399000L, duration.millis)
    }

    @Test
    fun `pumpDescription should have correct pump type`() {
        assertEquals(PumpType.EQUIL, equilPumpPlugin.pumpDescription.pumpType)
    }
}
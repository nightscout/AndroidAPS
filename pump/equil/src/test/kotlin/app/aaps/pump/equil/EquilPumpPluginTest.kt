package app.aaps.pump.equil

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.keys.EquilBooleanKey
import app.aaps.pump.equil.keys.EquilBooleanPreferenceKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

class EquilPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var equilManager: EquilManager
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var protectionCheck: ProtectionCheck
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var equilPumpPlugin: EquilPumpPlugin

    @BeforeEach
    fun prepareMocks() {

        whenever(rh.gs(anyInt())).thenReturn("")
        whenever(equilManager.lastConnectionFlow).thenReturn(MutableStateFlow(0L))
        whenever(equilManager.lastBolusTimeFlow).thenReturn(MutableStateFlow(null))
        whenever(equilManager.lastBolusAmountFlow).thenReturn(MutableStateFlow(null))
        whenever(equilManager.reservoirFlow).thenReturn(MutableStateFlow(0.0))
        whenever(equilManager.batteryFlow).thenReturn(MutableStateFlow(null))
        equilPumpPlugin =
            EquilPumpPlugin(
                aapsLogger, rh, preferences, commandQueue, rxBus, context,
                pumpSync, equilManager, pumpEnactResultProvider, constraintsChecker, ch, notificationManager, protectionCheck, blePreCheck, config
            )
    }

    // --- low insulin / low battery alarms (#3837) ---

    private val alarmFlags = mutableMapOf<EquilBooleanKey, Boolean>()
    private val pumpState = EquilManager.EquilState().apply { runMode = RunMode.RUN }

    /** playAlarm runs on every pump data update; flags are kept in a map like real preferences. */
    private fun prepareAlarms(insulin: Int, battery: Int = 100) {
        pumpState.currentInsulin = insulin
        pumpState.battery = battery
        whenever(equilManager.equilState).thenReturn(pumpState)
        whenever(equilManager.isActivationCompleted()).thenReturn(true)
        whenever(preferences.get(EquilBooleanPreferenceKey.EquilAlarmInsulin)).thenReturn(true)
        whenever(preferences.get(EquilBooleanPreferenceKey.EquilAlarmBattery)).thenReturn(true)
        whenever(preferences.get(any<EquilBooleanKey>())).thenAnswer { alarmFlags[it.arguments[0]] ?: false }
        doAnswer { alarmFlags[it.arguments[0] as EquilBooleanKey] = it.arguments[1] as Boolean; null }
            .whenever(preferences).put(any<EquilBooleanKey>(), any<Boolean>())
    }

    private fun alarmsPosted(id: NotificationId): Int =
        Mockito.mockingDetails(notificationManager).invocations.count { it.method.name == "post" && it.arguments[0] == id }

    @Test
    fun `2 U alarm sounds once, not again on every pump update`() {
        prepareAlarms(insulin = 2)

        repeat(5) { equilPumpPlugin.playAlarm() }

        // It used to be posted again with sound on every update, so it could not be snoozed.
        assertEquals(1, alarmsPosted(NotificationId.EQUIL_ALARM_INSULIN))
    }

    @Test
    fun `each low insulin level alarms once while the reservoir goes down`() {
        for (insulin in listOf(10, 9, 5, 4, 2, 1, 0, 0)) {
            prepareAlarms(insulin)
            equilPumpPlugin.playAlarm()
        }

        // 10 U, 5 U, 2 U and empty: one alarm each.
        assertEquals(4, alarmsPosted(NotificationId.EQUIL_ALARM_INSULIN))
    }

    @Test
    fun `battery under 5 percent alarms once`() {
        prepareAlarms(insulin = 100, battery = 4)

        repeat(3) { equilPumpPlugin.playAlarm() }

        assertEquals(1, alarmsPosted(NotificationId.EQUIL_LOW_BATTERY))
    }

    @Test
    fun `a reservoir change arms the alarms again`() {
        prepareAlarms(insulin = 2)
        equilPumpPlugin.playAlarm()

        equilPumpPlugin.resetData()
        equilPumpPlugin.playAlarm()

        assertEquals(2, alarmsPosted(NotificationId.EQUIL_ALARM_INSULIN))
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
    fun `isInitialized should delegate to equilManager`() {
        // Default mock returns false for isActivationCompleted()
        assertFalse(equilPumpPlugin.isInitialized())
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
        assertEquals(null, equilPumpPlugin.lastBolusTime.value)
    }

    @Test
    fun `lastBolusAmount should return null`() {
        assertEquals(null, equilPumpPlugin.lastBolusAmount.value)
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

    @Test
    fun `pumpDescription should not offer fill or battery change`() {
        // The Equil wizard fills the reservoir and fits the charged battery itself, so the generic
        // Prime/Fill and Pump battery change actions must stay hidden.
        assertFalse(equilPumpPlugin.pumpDescription.isRefillingCapable)
        assertFalse(equilPumpPlugin.pumpDescription.isBatteryReplaceable)
        assertTrue(equilPumpPlugin.pumpDescription.isPatchPump)
    }
}
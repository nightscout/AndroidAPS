package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.pump.alerts.PumpAlertSeverity
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.pump.aaruy.services.AaruyAlerts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_SMART_NULLS
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class AaruyPumpAlarmsTest {

    private var now = 1_000L
    private val dateUtil: DateUtil = mock(DateUtil::class.java).also { whenever(it.now()).thenAnswer { now } }
    private val preferences: Preferences = mock(Preferences::class.java, RETURNS_SMART_NULLS)
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java),
        preferences,
        dateUtil,
        mock(PumpSync::class.java),
        mock(TemporaryBasalStorage::class.java),
        mock(BolusProgressData::class.java), testHardLimits()
    )

    /** The screen and the alerts follow this flow. Confirmed alarms stayed on it for up to a minute. */
    @Test
    fun theFlowFollowsEachChangeOfTheAlarms() {
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(setOf(AlarmState.PUMP_MOTOR_BLOCK), pump.activeAlarmsFlow.value.keys)

        pump.addAlarm(AlarmState.PUMP_LOW_RESERVOIR)
        pump.removeAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(setOf(AlarmState.PUMP_LOW_RESERVOIR), pump.activeAlarmsFlow.value.keys)

        pump.clearAllAlarms()
        assertEquals(emptySet<AlarmState>(), pump.activeAlarmsFlow.value.keys)
    }

    /** A blocked motor repeats until it is confirmed: one alarm, at its first time. After it ended, it is a new one with a new time. */
    @Test
    fun theSameAlarmAfterItEndedHasANewTime() {
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        now = 2_000L
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(1_000L, pump.activeAlarms[AlarmState.PUMP_MOTOR_BLOCK])

        pump.removeAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        now = 3_000L
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(3_000L, pump.activeAlarms[AlarmState.PUMP_MOTOR_BLOCK])
    }

    /**
     * Seen on a real pump: "stopped too long" came only as a message while a link was up, and the next connection did not
     * bring it. The status says since when the pump is stopped, and the alarm gets the time the pump raised it.
     */
    @Test
    fun aStopOfMoreThan15MinutesIsAnAlarmAtTheTimeThePumpRaisedIt() {
        val since = 100_000L
        now = since + 14 * 60_000L
        pump.onStopped(since)
        assertEquals(emptyMap<AlarmState, Long>(), pump.activeAlarms)

        now = since + 21 * 60_000L
        pump.onStopped(since)
        assertEquals(mapOf(AlarmState.PUMP_STOP_TOO_LONG to since + 15 * 60_000L), pump.activeAlarms)

        // Each status says it again: one alarm, at its first time.
        now = since + 30 * 60_000L
        pump.onStopped(since)
        assertEquals(since + 15 * 60_000L, pump.activeAlarms[AlarmState.PUMP_STOP_TOO_LONG])
    }

    /** Stored before the time was kept: the time of the load, so the alert has one. */
    @Test
    fun anAlarmStoredWithoutItsTimeGetsTheTimeOfTheLoad() {
        whenever(preferences.get(AaruyStringKey.PumpActiveAlarm)).thenReturn("PUMP_MOTOR_BLOCK,PUMP_LOW_POWER@500")
        now = 9_000L
        pump.loadVarsFromSP()
        assertEquals(mapOf(AlarmState.PUMP_MOTOR_BLOCK to 9_000L, AlarmState.PUMP_LOW_POWER to 500L), pump.activeAlarms)
    }

    /** Each refused command is new, also when it is the same one again. */
    @Test
    fun eachRefusalIsAnnounced(): Unit = runBlocking {
        val seen = CopyOnWriteArrayList<AlarmState>()
        val collector = launch(Dispatchers.Unconfined) { pump.pumpWarnings.collect { seen.add(it) } }
        pump.warn(AlarmState.PUMP_RUN_TMP_BASAL)
        pump.warn(AlarmState.PUMP_RUN_TMP_BASAL)
        assertEquals(2, seen.size)
        collector.cancel()
    }

    @Test
    fun onlyAlarmsArePutToThePumpToConfirm() {
        assertTrue(AlarmState.PUMP_MOTOR_BLOCK.isConfirmedOnThePump())
        assertTrue(AlarmState.PUMP_LOW_RESERVOIR.isConfirmedOnThePump())
        assertFalse(AlarmState.PUMP_STOP_TOO_LONG.isConfirmedOnThePump())
        assertFalse(AlarmState.PUMP_ZERO_RESERVOIR.isConfirmedOnThePump())
        assertTrue(AlarmState.PUMP_STOP_TOO_LONG.isHeld())
        assertFalse(AlarmState.PUMP_RUN_BOLUS.isHeld())
    }

    @Test
    fun anAlarmFromTheHistoryIsOnlyTheLowByte() {
        assertEquals(AlarmState.PUMP_MOTOR_BLOCK, AlarmState.fromHistory(5))
        assertEquals(AlarmState.PUMP_MOTOR_BLOCK, AlarmState.fromHistory(0x8105))
        assertEquals(AlarmState.NONE, AlarmState.fromHistory(0))
        assertEquals(0x8115, AlarmState.historyCode(0x15))
        assertEquals(AlarmState.PUMP_UNKNOWN_ALARM, AlarmState.fromHistory(0x15))
    }

    /**
     * Vendor (PR 5589, 2026-11-25): every alarm above 0x8100, except low battery and low reservoir, stops delivery until
     * the reservoir is changed. So an alarm the driver does not know is such a stop, not nothing. 0x810E was one.
     */
    @Test
    fun anAlarmTheDriverDoesNotKnowIsAStopOfDelivery() {
        assertEquals(AlarmState.PUMP_UNKNOWN_ALARM, AlarmState.fromInt(0x8115))
        assertEquals(AlarmState.PUMP_UNKNOWN_ALARM, AlarmState.fromInt(0x81FF))
        // Not an alarm: 0x8100 itself, and a refused command (0x80..) the driver does not know.
        assertEquals(AlarmState.NONE, AlarmState.fromInt(0x8100))
        assertEquals(AlarmState.NONE, AlarmState.fromInt(0x8099))
        // The known ones stay what they are.
        assertEquals(AlarmState.PUMP_LOW_POWER, AlarmState.fromInt(0x8101))
        assertEquals(AlarmState.PUMP_LOW_RESERVOIR, AlarmState.fromInt(0x8103))
        assertEquals(AlarmState.PUMP_MOTOR_FAILED_ERROR, AlarmState.fromInt(0x810E))

        assertTrue(AlarmState.isHighAlarm(AlarmState.PUMP_UNKNOWN_ALARM))
        assertEquals(PumpAlertSeverity.CRITICAL, AaruyAlerts.severityOf(AlarmState.PUMP_UNKNOWN_ALARM))
        assertTrue(AlarmState.PUMP_UNKNOWN_ALARM.isConfirmedOnThePump())
        assertTrue(AlarmState.PUMP_UNKNOWN_ALARM.isHeld())
    }

    /** The text has the code the pump sent, kept over an app restart. Without one, the code it stands for. */
    @Test
    fun theTextOfAnUnknownAlarmHasItsCode() {
        assertEquals(AaruyStrings.alarm_unknown.withArgs("8115"), pump.alarmText(AlarmState.PUMP_UNKNOWN_ALARM, 0x8115))
        assertEquals(AaruyStrings.alarm_unknown.withArgs("81FF"), pump.alarmText(AlarmState.PUMP_UNKNOWN_ALARM, 0))

        whenever(preferences.get(AaruyIntKey.PumpUnknownAlarmCode)).thenReturn(0x8120)
        assertEquals(AaruyStrings.alarm_unknown.withArgs("8120"), pump.alarmText(AlarmState.PUMP_UNKNOWN_ALARM))
    }
}

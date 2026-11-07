package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.common.enums.AlarmState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AaruyPumpAlarmsTest {

    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java),
        mock(Preferences::class.java),
        mock(DateUtil::class.java),
        mock(PumpSync::class.java),
        mock(TemporaryBasalStorage::class.java),
        mock(BolusProgressData::class.java)
    )

    /** The screen redraws on this flow. Confirmed alarms stayed on it for up to a minute. */
    @Test
    fun theFlowFollowsEachChangeOfTheAlarms() {
        pump.addAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(setOf(AlarmState.PUMP_MOTOR_BLOCK), pump.activeAlarmsFlow.value)

        pump.addAlarm(AlarmState.PUMP_LOW_RESERVOIR)
        pump.removeAlarm(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(setOf(AlarmState.PUMP_LOW_RESERVOIR), pump.activeAlarmsFlow.value)

        pump.clearAlarmState()
        assertEquals(emptySet<AlarmState>(), pump.activeAlarmsFlow.value)
    }

    /** A blocked motor repeats until it is confirmed: one alarm. The same alarm after the confirm is a new one and must ring again. */
    @Test
    fun theSameAlarmRingsAgainAfterItWasConfirmed(): Unit = runBlocking {
        val seen = CopyOnWriteArrayList<AlarmState>()
        val collector = launch(Dispatchers.Unconfined) { pump.pumpWarnings.collect { seen.add(it) } }
        pump.warnOnce(AlarmState.PUMP_MOTOR_BLOCK)
        pump.warnOnce(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(listOf(AlarmState.PUMP_MOTOR_BLOCK), seen.toList())

        pump.clearAlarmState()
        pump.warnOnce(AlarmState.PUMP_MOTOR_BLOCK)
        assertEquals(listOf(AlarmState.PUMP_MOTOR_BLOCK, AlarmState.PUMP_MOTOR_BLOCK), seen.toList())
        collector.cancel()
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
    fun anAlarmFromTheHistoryIsOnlyTheLowByte() {
        assertEquals(AlarmState.PUMP_MOTOR_BLOCK, AlarmState.fromHistory(5))
        assertEquals(AlarmState.PUMP_MOTOR_BLOCK, AlarmState.fromHistory(0x8105))
        assertEquals(AlarmState.NONE, AlarmState.fromHistory(0))
    }
}

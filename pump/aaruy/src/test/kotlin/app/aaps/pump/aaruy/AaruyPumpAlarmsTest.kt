package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.common.enums.AlarmState
import org.junit.jupiter.api.Assertions.assertEquals
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
}

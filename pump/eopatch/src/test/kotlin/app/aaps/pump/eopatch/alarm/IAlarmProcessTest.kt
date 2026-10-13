package app.aaps.pump.eopatch.alarm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IAlarmProcessTest {

    @Test
    fun `ALARM_UNHANDLED constant should be 0`() {
        assertThat(IAlarmProcess.ALARM_UNHANDLED).isEqualTo(0)
    }

    @Test
    fun `ALARM_PAUSE constant should be 1`() {
        assertThat(IAlarmProcess.ALARM_PAUSE).isEqualTo(1)
    }

    @Test
    fun `ALARM_HANDLED constant should be 2`() {
        assertThat(IAlarmProcess.ALARM_HANDLED).isEqualTo(2)
    }

    @Test
    fun `ALARM_HANDLED_BUT_NEED_STOP_BEEP constant should be 3`() {
        assertThat(IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP).isEqualTo(3)
    }

    @Test
    fun `all constants should be distinct`() {
        val constants = setOf(
            IAlarmProcess.ALARM_UNHANDLED,
            IAlarmProcess.ALARM_PAUSE,
            IAlarmProcess.ALARM_HANDLED,
            IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP
        )

        assertThat(constants).hasSize(4)
    }

    @Test
    fun `constants should be in sequential order`() {
        assertThat(IAlarmProcess.ALARM_UNHANDLED).isLessThan(IAlarmProcess.ALARM_PAUSE)
        assertThat(IAlarmProcess.ALARM_PAUSE).isLessThan(IAlarmProcess.ALARM_HANDLED)
        assertThat(IAlarmProcess.ALARM_HANDLED).isLessThan(IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP)
    }
}

package app.aaps.pump.eopatch.alarm

import io.reactivex.rxjava3.core.Single

interface IAlarmProcess {

    fun doAction(code: AlarmCode): Single<Int>

    companion object {

        const val ALARM_UNHANDLED = 0
        const val ALARM_HANDLED = 2
        const val ALARM_HANDLED_BUT_NEED_STOP_BEEP = 3
    }
}

package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import kotlinx.coroutines.flow.Flow

interface CarelevoAlarmInfoRepository {

    /** The stored alarms, and every change of them. An empty list when the store was cleared or cannot be read. */
    fun observeAlarms(): Flow<List<CarelevoAlarmInfo>>

    /** One-shot read of ALL stored alarms. Everything stored is active — acknowledging removes. */
    fun getAlarmsOnce(): List<CarelevoAlarmInfo>
    fun setAlarms(list: List<CarelevoAlarmInfo>)
    fun upsertAlarm(alarm: CarelevoAlarmInfo)

    /** Remove one alarm from the store (= acknowledge; there is no acknowledged-alarm history). */
    fun removeAlarm(alarmId: String)
    fun clearAlarms()
}
package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoAlarmInfoDao {

    fun getAlarms(): Flow<List<CarelevoAlarmInfoEntity>?>

    /** One-shot read of ALL stored alarms. Everything stored is active — acknowledging removes. */
    fun getAlarmsOnce(): List<CarelevoAlarmInfoEntity>
    fun setAlarms(list: List<CarelevoAlarmInfoEntity>)
    fun clearAlarms()
    fun upsertAlarm(entity: CarelevoAlarmInfoEntity)

    /** Remove one alarm from the store (= acknowledge; there is no acknowledged-alarm history). */
    fun removeAlarm(alarmId: String)
}
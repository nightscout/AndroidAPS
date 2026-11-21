package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoAlarmInfoLocalDataSource {

    fun observeAlarms(): Flow<List<CarelevoAlarmInfoEntity>?>

    fun getAlarmsOnce(): List<CarelevoAlarmInfoEntity>
    fun setAlarms(list: List<CarelevoAlarmInfoEntity>)
    fun upsertAlarm(entity: CarelevoAlarmInfoEntity)
    fun removeAlarm(alarmId: String)
    fun clearAlarms()
}
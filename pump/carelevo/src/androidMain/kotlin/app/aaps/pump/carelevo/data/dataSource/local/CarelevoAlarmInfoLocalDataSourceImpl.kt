package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AppScope::class)
class CarelevoAlarmInfoLocalDataSourceImpl @Inject constructor(
    private val dao: CarelevoAlarmInfoDao
) : CarelevoAlarmInfoLocalDataSource {

    override fun observeAlarms(): Flow<List<CarelevoAlarmInfoEntity>?> =
        dao.getAlarms()

    override fun getAlarmsOnce(): List<CarelevoAlarmInfoEntity> = dao.getAlarmsOnce()

    override fun setAlarms(list: List<CarelevoAlarmInfoEntity>) =
        dao.setAlarms(list)

    override fun upsertAlarm(entity: CarelevoAlarmInfoEntity) =
        dao.upsertAlarm(entity)

    override fun removeAlarm(alarmId: String) =
        dao.removeAlarm(alarmId)

    override fun clearAlarms() =
        dao.clearAlarms()
}

package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import app.aaps.pump.carelevo.data.mapper.transformToDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToEntity
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class CarelevoAlarmInfoLocalRepositoryImpl @Inject constructor(
    private val dataSource: CarelevoAlarmInfoLocalDataSource
) : CarelevoAlarmInfoRepository {

    override fun observeAlarms(): Flow<List<CarelevoAlarmInfo>> =
        dataSource.observeAlarms()
            .map { list -> list.orEmpty().map { it.transformToDomainModel() } }

    override fun getAlarmsOnce(): List<CarelevoAlarmInfo> =
        dataSource.getAlarmsOnce().map { it.transformToDomainModel() }

    override fun setAlarms(list: List<CarelevoAlarmInfo>) =
        dataSource.setAlarms(list.map { it.transformToEntity() })

    override fun upsertAlarm(alarm: CarelevoAlarmInfo) =
        dataSource.upsertAlarm(alarm.transformToEntity())

    override fun removeAlarm(alarmId: String) =
        dataSource.removeAlarm(alarmId)

    override fun clearAlarms() =
        dataSource.clearAlarms()
}

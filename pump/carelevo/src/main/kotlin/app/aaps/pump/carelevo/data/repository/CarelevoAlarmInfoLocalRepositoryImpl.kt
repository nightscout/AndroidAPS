package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import app.aaps.pump.carelevo.data.mapper.transformToDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToEntity
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional
import javax.inject.Inject

class CarelevoAlarmInfoLocalRepositoryImpl @Inject constructor(
    private val dataSource: CarelevoAlarmInfoLocalDataSource
) : CarelevoAlarmInfoRepository {

    override fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfo>>> =
        dataSource.observeAlarms() // Observable<Optional<List<CarelevoAlarmInfoEntity>>>
            .map { opt ->
                val list = opt.orElse(emptyList())
                    .map { it.transformToDomainModel() }
                Optional.of(list)
            }

    override fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfo>>> =
        dataSource.getAlarmsOnce() // Single<Optional<List<Entity>>>
            .map { opt ->
                val list = opt.orElse(emptyList())
                    .map { it.transformToDomainModel() }
                Optional.of(list)
            }

    override fun setAlarms(list: List<CarelevoAlarmInfo>): Completable =
        dataSource.setAlarms(list.map { it.transformToEntity() })

    override fun upsertAlarm(alarm: CarelevoAlarmInfo): Completable =
        dataSource.upsertAlarm(alarm.transformToEntity())

    override fun removeAlarm(alarmId: String): Completable =
        dataSource.removeAlarm(alarmId)

    override fun clearAlarms(): Completable =
        dataSource.clearAlarms()
}
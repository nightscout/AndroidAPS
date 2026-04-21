package info.nightscout.androidaps.plugins.pump.carelevo.data.repository

import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToEntity
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
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

    override fun getAlarmsOnce(includeUnacknowledged: Boolean): Single<Optional<List<CarelevoAlarmInfo>>> =
        dataSource.getAlarmsOnce(includeUnacknowledged) // Single<Optional<List<Entity>>>
            .map { opt ->
                val list = opt.orElse(emptyList())
                    .map { it.transformToDomainModel() }
                Optional.of(list)
            }

    override fun setAlarms(list: List<CarelevoAlarmInfo>): Completable =
        dataSource.setAlarms(list.map { it.transformToEntity() })

    override fun upsertAlarm(alarm: CarelevoAlarmInfo): Completable =
        dataSource.upsertAlarm(alarm.transformToEntity())

    override fun markAcknowledged(alarmId: String, acknowledged: Boolean, updatedAt: String): Completable =
        dataSource.markAcknowledged(alarmId, acknowledged, updatedAt)

    override fun clearAlarms(): Completable =
        dataSource.clearAlarms()
}
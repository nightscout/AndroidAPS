package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local

import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional
import javax.inject.Inject

class CarelevoAlarmInfoLocalDataSourceImpl @Inject constructor(
    private val dao: CarelevoAlarmInfoDao
) : CarelevoAlarmInfoLocalDataSource {

    override fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfoEntity>>> =
        dao.getAlarms()

    override fun getAlarmsOnce(includeUnacknowledged: Boolean): Single<Optional<List<CarelevoAlarmInfoEntity>>> = dao.getAlarmsOnce(includeUnacknowledged)

    override fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable =
        dao.setAlarms(list)

    override fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable =
        dao.upsertAlarm(entity)

    override fun markAcknowledged(alarmId: String, acknowledged: Boolean, updatedAt: String): Completable =
        dao.markAcknowledged(alarmId, acknowledged, updatedAt)

    override fun clearAlarms(): Completable =
        dao.clearAlarms()
}
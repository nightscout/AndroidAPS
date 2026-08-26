package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
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

    override fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfoEntity>>> = dao.getAlarmsOnce()

    override fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable =
        dao.setAlarms(list)

    override fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable =
        dao.upsertAlarm(entity)

    override fun removeAlarm(alarmId: String): Completable =
        dao.removeAlarm(alarmId)

    override fun clearAlarms(): Completable =
        dao.clearAlarms()
}
package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional

interface CarelevoAlarmInfoLocalDataSource {

    fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfoEntity>>>

    fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfoEntity>>>
    fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable
    fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable
    fun removeAlarm(alarmId: String): Completable
    fun clearAlarms(): Completable
}
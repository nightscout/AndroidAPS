package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional

interface CarelevoAlarmInfoLocalDataSource {

    fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfoEntity>>>

    fun getAlarmsOnce(includeUnacknowledged: Boolean = true): Single<Optional<List<CarelevoAlarmInfoEntity>>>
    fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable
    fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable
    fun markAcknowledged(alarmId: String, acknowledged: Boolean, updatedAt: String): Completable
    fun clearAlarms(): Completable
}
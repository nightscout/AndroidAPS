package info.nightscout.androidaps.plugins.pump.carelevo.data.dao

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional

interface CarelevoAlarmInfoDao {

    fun getAlarms(): Observable<Optional<List<CarelevoAlarmInfoEntity>>>
    fun getAlarmsOnce(includeUnacknowledged: Boolean = true): Single<Optional<List<CarelevoAlarmInfoEntity>>>
    fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable
    fun clearAlarms(): Completable
    fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable
    fun markAcknowledged(alarmId: String, acknowledged: Boolean, updatedAt: String): Completable
}
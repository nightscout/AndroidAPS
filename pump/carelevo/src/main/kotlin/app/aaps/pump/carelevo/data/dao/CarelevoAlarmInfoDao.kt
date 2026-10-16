package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional

interface CarelevoAlarmInfoDao {

    fun getAlarms(): Observable<Optional<List<CarelevoAlarmInfoEntity>>>

    /** One-shot read of ALL stored alarms. Everything stored is active — acknowledging removes. */
    fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfoEntity>>>
    fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable
    fun clearAlarms(): Completable
    fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable

    /** Remove one alarm from the store (= acknowledge; there is no acknowledged-alarm history). */
    fun removeAlarm(alarmId: String): Completable
}
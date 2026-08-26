package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional

interface CarelevoAlarmInfoRepository {

    fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfo>>>

    /** One-shot read of ALL stored alarms. Everything stored is active — acknowledging removes. */
    fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfo>>>
    fun setAlarms(list: List<CarelevoAlarmInfo>): Completable
    fun upsertAlarm(alarm: CarelevoAlarmInfo): Completable

    /** Remove one alarm from the store (= acknowledge; there is no acknowledged-alarm history). */
    fun removeAlarm(alarmId: String): Completable
    fun clearAlarms(): Completable
}
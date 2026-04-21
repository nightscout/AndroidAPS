package info.nightscout.androidaps.plugins.pump.carelevo.domain.repository

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional

interface CarelevoAlarmInfoRepository {

    fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfo>>>
    fun getAlarmsOnce(includeUnacknowledged: Boolean = true): Single<Optional<List<CarelevoAlarmInfo>>>
    fun setAlarms(list: List<CarelevoAlarmInfo>): Completable
    fun upsertAlarm(alarm: CarelevoAlarmInfo): Completable
    fun markAcknowledged(alarmId: String, acknowledged: Boolean, updatedAt: String): Completable
    fun clearAlarms(): Completable
}
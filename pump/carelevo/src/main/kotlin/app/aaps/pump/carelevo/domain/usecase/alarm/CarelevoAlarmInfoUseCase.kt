package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional
import javax.inject.Inject

/** Domain seam over [CarelevoAlarmInfoRepository]: the persisted set of ACTIVE patch alarms. */
class CarelevoAlarmInfoUseCase @Inject constructor(
    private val repository: CarelevoAlarmInfoRepository
) {

    fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfo>>> =
        repository.observeAlarms()

    /** One-shot read of all stored (= active) alarms. */
    fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfo>>> = repository.getAlarmsOnce()

    fun upsertAlarm(alarm: CarelevoAlarmInfo): Completable =
        repository.upsertAlarm(alarm)

    /** Acknowledge = remove from the store; there is no acknowledged-alarm history. */
    fun acknowledgeAlarm(alarmId: String): Completable =
        repository.removeAlarm(alarmId)

    fun clearAlarms(): Completable =
        repository.clearAlarms()
}
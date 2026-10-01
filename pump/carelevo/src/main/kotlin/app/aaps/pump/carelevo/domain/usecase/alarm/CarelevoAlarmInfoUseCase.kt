package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.util.Optional
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Domain seam over [CarelevoAlarmInfoRepository]: the persisted set of ACTIVE patch alarms. */
// App-scoped: a view model injects this, and ViewModelOwnershipTest requires every concrete class
// a view model injects to have one owner. It is a stateless view over the app-scoped DAOs, so one
// instance is also the only sensible number.
@SingleIn(AppScope::class)
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
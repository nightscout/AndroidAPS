package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import javax.inject.Inject

class CarelevoAlarmInfoUseCase @Inject constructor(
    private val repository: CarelevoAlarmInfoRepository
) {

    fun observeAlarms(): Observable<Optional<List<CarelevoAlarmInfo>>> =
        repository.observeAlarms()

    fun getAlarmsOnce(includeUnacknowledged: Boolean = false): Single<Optional<List<CarelevoAlarmInfo>>> = repository.getAlarmsOnce(includeUnacknowledged)

    fun upsertAlarm(alarm: CarelevoAlarmInfo): Completable =
        repository.upsertAlarm(alarm)

    fun acknowledgeAlarm(alarmId: String): Completable =
        repository.markAcknowledged(alarmId, acknowledged = true, updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")))

    fun clearAlarms(): Completable =
        repository.clearAlarms()
}
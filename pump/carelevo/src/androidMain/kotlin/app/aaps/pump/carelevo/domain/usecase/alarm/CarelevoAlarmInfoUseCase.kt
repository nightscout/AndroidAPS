package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Domain seam over [CarelevoAlarmInfoRepository]: the persisted set of ACTIVE patch alarms.
 *
 * The store reads and writes preferences, so every one-shot call runs on the IO dispatcher. A failed write throws.
 */
// App-scoped: a view model injects this, and ViewModelOwnershipTest requires every concrete class
// a view model injects to have one owner. It is a stateless view over the app-scoped DAOs, so one
// instance is also the only sensible number.
@SingleIn(AppScope::class)
class CarelevoAlarmInfoUseCase @Inject constructor(
    private val repository: CarelevoAlarmInfoRepository
) {

    fun observeAlarms(): Flow<List<CarelevoAlarmInfo>> =
        repository.observeAlarms()

    /** One-shot read of all stored (= active) alarms. */
    suspend fun getAlarmsOnce(): List<CarelevoAlarmInfo> = withContext(aapsIoDispatcher) { repository.getAlarmsOnce() }

    suspend fun upsertAlarm(alarm: CarelevoAlarmInfo) = withContext(aapsIoDispatcher) {
        repository.upsertAlarm(alarm)
    }

    /** Acknowledge = remove from the store; there is no acknowledged-alarm history. */
    suspend fun acknowledgeAlarm(alarmId: String) = withContext(aapsIoDispatcher) {
        repository.removeAlarm(alarmId)
    }

    suspend fun clearAlarms() = withContext(aapsIoDispatcher) {
        repository.clearAlarms()
    }
}

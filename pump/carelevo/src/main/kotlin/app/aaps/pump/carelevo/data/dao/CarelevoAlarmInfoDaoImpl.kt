package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.config.PrefEnvConfig
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import jakarta.inject.Inject
import java.util.Optional

class CarelevoAlarmInfoDaoImpl @Inject constructor(
    private val prefManager: SP
) : CarelevoAlarmInfoDao {

    private val _alarms: BehaviorSubject<Optional<List<CarelevoAlarmInfoEntity>>> = BehaviorSubject.create()

    override fun getAlarms(): Observable<Optional<List<CarelevoAlarmInfoEntity>>> {
        // Cold-load through the SAME unfiltered loader every other method uses. The original
        // vendor code filtered this one path to `it.acknowledged` — which is never true, because
        // acknowledging DELETES the entity (see removeAlarm) — so every persisted active alarm
        // (occlusion, out of insulin, …) silently vanished from the stream on process restart.
        if (_alarms.value == null) {
            runCatching { ensureLoaded() }
                .onFailure { e ->
                    e.printStackTrace()
                    _alarms.onNext(Optional.ofNullable(null))
                }
        }
        return _alarms
    }

    override fun getAlarmsOnce(): Single<Optional<List<CarelevoAlarmInfoEntity>>> {
        // Everything in the store is an active (unacknowledged) alarm by construction — an
        // acknowledged alarm is removed, not flagged.
        return Single.fromCallable { Optional.of(ensureLoaded()) }
    }

    override fun setAlarms(list: List<CarelevoAlarmInfoEntity>): Completable {
        return Completable.fromAction {
            saveList(list)
            _alarms.onNext(Optional.of(list))
        }
    }

    override fun clearAlarms(): Completable = Completable.fromAction {
        prefManager.remove(PrefEnvConfig.CARELEVO_ALARM_INFO_LIST)
        _alarms.onNext(Optional.ofNullable(null))
    }

    override fun upsertAlarm(entity: CarelevoAlarmInfoEntity): Completable {
        return Completable.fromAction {
            val current = ensureLoaded()

            val idx = current.indexOfFirst {
                it.alarmType == entity.alarmType &&
                    it.cause == entity.cause &&
                    !it.acknowledged
            }

            val next = if (idx >= 0) {
                current.toMutableList().apply {
                    val existing = this[idx]
                    this[idx] = existing.copy(
                        updatedAt = entity.updatedAt,
                        occurrenceCount = existing.occurrenceCount + 1
                    )
                }
            } else {
                current + entity.copy(occurrenceCount = 1)
            }

            saveList(next)
            _alarms.onNext(Optional.of(next))
        }
    }

    override fun removeAlarm(alarmId: String): Completable {
        return Completable.fromAction {
            val current = ensureLoaded()
            val next = current.filterNot { it.alarmId == alarmId }

            saveList(next)
            _alarms.onNext(Optional.of(next))
        }
    }

    private fun ensureLoaded(): List<CarelevoAlarmInfoEntity> {
        val cached = _alarms.value?.orElse(null)
        if (cached != null) return cached

        val json = prefManager.getString(PrefEnvConfig.CARELEVO_ALARM_INFO_LIST, "")
        val list = if (json.isBlank()) emptyList() else CarelevoGsonHelper.sharedGson()
            .fromJson(json, Array<CarelevoAlarmInfoEntity>::class.java)
            .toList()
        _alarms.onNext(Optional.of(list))
        return list
    }

    private fun saveList(list: List<CarelevoAlarmInfoEntity>) {
        val json = CarelevoGsonHelper.sharedGson().toJson(list)
        prefManager.putString(PrefEnvConfig.CARELEVO_ALARM_INFO_LIST, json)
    }
}

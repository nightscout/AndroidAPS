package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.common.latest
import app.aaps.pump.carelevo.common.latestValueFlow
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.domain.type.AlarmCause
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarelevoAlarmInfoDaoImpl @Inject constructor(
    private val preferences: Preferences
) : CarelevoAlarmInfoDao {

    /** Null after a clear or a store that cannot be read; no value before the first load. */
    private val _alarms: MutableSharedFlow<List<CarelevoAlarmInfoEntity>?> = latestValueFlow()

    /**
     * Every read-change-write of the list holds this. Alarms come from the patch session, the snapshot read and the
     * screens at the same time, and without it two writes that overlap keep only the last one.
     */
    private val lock = AapsLock()

    override fun getAlarms(): Flow<List<CarelevoAlarmInfoEntity>?> = lock.withLock {
        // Cold-load through the SAME unfiltered loader every other method uses. The original
        // vendor code filtered this one path to `it.acknowledged` — which is never true, because
        // acknowledging DELETES the entity (see removeAlarm) — so every persisted active alarm
        // (occlusion, out of insulin, …) silently vanished from the stream on process restart.
        if (_alarms.replayCache.isEmpty()) {
            runCatching { ensureLoaded() }
                .onFailure { e ->
                    e.printStackTrace()
                    _alarms.tryEmit(null)
                }
        }
        _alarms
    }

    // Everything in the store is an active (unacknowledged) alarm by construction — an
    // acknowledged alarm is removed, not flagged.
    override fun getAlarmsOnce(): List<CarelevoAlarmInfoEntity> = lock.withLock { ensureLoaded() }

    override fun setAlarms(list: List<CarelevoAlarmInfoEntity>) {
        lock.withLock {
            saveList(list)
            _alarms.tryEmit(list)
        }
    }

    override fun clearAlarms() {
        lock.withLock {
            preferences.remove(CarelevoStringNonKey.AlarmInfoList)
            _alarms.tryEmit(null)
        }
    }

    override fun upsertAlarm(entity: CarelevoAlarmInfoEntity) {
        lock.withLock { upsertLocked(entity) }
    }

    override fun removeAlarm(alarmId: String) {
        lock.withLock {
            val next = ensureLoaded().filterNot { it.alarmId == alarmId }
            saveList(next)
            _alarms.tryEmit(next)
        }
    }

    private fun upsertLocked(entity: CarelevoAlarmInfoEntity) {
        val current = ensureLoaded()

        val idx = current.indexOfFirst {
            !it.acknowledged && it.cause.canonicalKey() == entity.cause.canonicalKey()
        }

        val next = if (idx >= 0) {
            current.toMutableList().apply {
                val existing = this[idx]
                val replacementCause =
                    if (entity.cause.priority() > existing.cause.priority()) entity.cause else existing.cause
                val replacementAlarmType =
                    if (entity.cause.priority() > existing.cause.priority()) entity.alarmType else existing.alarmType
                val replacementValue =
                    if (entity.cause.priority() > existing.cause.priority()) entity.value else existing.value
                this[idx] = existing.copy(
                    alarmType = replacementAlarmType,
                    cause = replacementCause,
                    value = replacementValue,
                    updatedAt = entity.updatedAt,
                    occurrenceCount = existing.occurrenceCount + 1
                )
            }
        } else {
            current + entity.copy(occurrenceCount = 1)
        }

        saveList(next)
        _alarms.tryEmit(next)
    }

    private fun ensureLoaded(): List<CarelevoAlarmInfoEntity> {
        val cached = _alarms.latest
        if (cached != null) return cached

        val json = preferences.get(CarelevoStringNonKey.AlarmInfoList)
        val list = if (json.isBlank()) emptyList() else CarelevoGsonHelper.sharedGson()
            .fromJson(json, Array<CarelevoAlarmInfoEntity>::class.java)
            .toList()
        _alarms.tryEmit(list)
        return list
    }

    private fun saveList(list: List<CarelevoAlarmInfoEntity>) {
        val json = CarelevoGsonHelper.sharedGson().toJson(list)
        preferences.put(CarelevoStringNonKey.AlarmInfoList, json)
    }

    private fun AlarmCause.canonicalKey(): String = when (this) {
        AlarmCause.ALARM_WARNING_LOW_INSULIN,
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        AlarmCause.ALARM_NOTICE_LOW_INSULIN -> "OUT_OF_INSULIN"

        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED -> "PATCH_EXPIRED"

        AlarmCause.ALARM_WARNING_LOW_BATTERY,
        AlarmCause.ALARM_ALERT_LOW_BATTERY -> "LOW_BATTERY"

        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE -> "INVALID_TEMPERATURE"

        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF,
        AlarmCause.ALARM_ALERT_APP_NO_USE -> "AUTO_OFF"

        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> "BLE_NOT_CONNECTED"

        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE -> "PATCH_APP_INCOMPLETE"

        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT -> "START_INSULIN"

        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED -> "SELF_DIAGNOSIS_FAILED"

        AlarmCause.ALARM_WARNING_PATCH_ERROR -> "PATCH_ERROR"

        AlarmCause.ALARM_WARNING_PUMP_CLOGGED -> "OCCLUSION_DETECTED"

        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR -> "NEEDLE_INSERTION_ERROR"

        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK -> "ATTACH_PATCH_CHECK"
        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED -> "TIME_ZONE_CHANGED"
        AlarmCause.ALARM_NOTICE_BG_CHECK -> "BG_CHECK"
        AlarmCause.ALARM_NOTICE_LGS_START -> "LGS_START"
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN -> "LGS_FINISHED"
        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING -> "LGS_NOT_WORKING"
        AlarmCause.ALARM_UNKNOWN -> "UNKNOWN"
    }

    private fun AlarmCause.priority(): Int = when (this) {
        AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_2,
        AlarmCause.ALARM_ALERT_PATCH_EXPIRED_PHASE_1,
        AlarmCause.ALARM_ALERT_LOW_BATTERY,
        AlarmCause.ALARM_ALERT_INVALID_TEMPERATURE,
        AlarmCause.ALARM_ALERT_APP_NO_USE,
        AlarmCause.ALARM_ALERT_BLE_NOT_CONNECTED,
        AlarmCause.ALARM_ALERT_PATCH_APPLICATION_INCOMPLETE,
        AlarmCause.ALARM_ALERT_RESUME_INSULIN_DELIVERY_TIMEOUT,
        AlarmCause.ALARM_ALERT_BLUETOOTH_OFF -> 3

        AlarmCause.ALARM_WARNING_LOW_INSULIN,
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED_PHASE_1,
        AlarmCause.ALARM_WARNING_LOW_BATTERY,
        AlarmCause.ALARM_WARNING_INVALID_TEMPERATURE,
        AlarmCause.ALARM_WARNING_NOT_USED_APP_AUTO_OFF,
        AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED,
        AlarmCause.ALARM_WARNING_INCOMPLETE_PATCH_SETTING,
        AlarmCause.ALARM_WARNING_SELF_DIAGNOSIS_FAILED,
        AlarmCause.ALARM_WARNING_PATCH_EXPIRED,
        AlarmCause.ALARM_WARNING_PATCH_ERROR,
        AlarmCause.ALARM_WARNING_PUMP_CLOGGED,
        AlarmCause.ALARM_WARNING_NEEDLE_INSERTION_ERROR -> 2

        AlarmCause.ALARM_NOTICE_LOW_INSULIN,
        AlarmCause.ALARM_NOTICE_PATCH_EXPIRED,
        AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK,
        AlarmCause.ALARM_NOTICE_BG_CHECK,
        AlarmCause.ALARM_NOTICE_TIME_ZONE_CHANGED,
        AlarmCause.ALARM_NOTICE_LGS_START,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_DISCONNECTED_PATCH_OR_CGM,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_PAUSE_LGS,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_TIME_OVER,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_OFF_LGS,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG,
        AlarmCause.ALARM_NOTICE_LGS_FINISHED_UNKNOWN,
        AlarmCause.ALARM_NOTICE_LGS_NOT_WORKING -> 1

        AlarmCause.ALARM_UNKNOWN -> 0
    }
}

package info.nightscout.androidaps.plugins.pump.carelevo.data.mapper

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType

fun CarelevoAlarmInfoEntity.transformToDomainModel(): CarelevoAlarmInfo =
    CarelevoAlarmInfo(
        alarmId = alarmId,
        alarmType = AlarmType.fromCode(alarmType),
        cause = cause,
        value = value,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isAcknowledged = acknowledged,
        occurrenceCount = occurrenceCount
    )

fun CarelevoAlarmInfo.transformToEntity(): CarelevoAlarmInfoEntity =
    CarelevoAlarmInfoEntity(
        alarmId = alarmId,
        alarmType = AlarmType.fromAlarmType(alarmType),
        cause = cause,
        value = value,
        createdAt = createdAt,
        updatedAt = updatedAt,
        acknowledged = isAcknowledged,
    )
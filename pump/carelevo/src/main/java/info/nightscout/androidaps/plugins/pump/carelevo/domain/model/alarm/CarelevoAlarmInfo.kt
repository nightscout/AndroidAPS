package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.alarm

import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType

data class CarelevoAlarmInfo(
    val alarmId: String,
    val alarmType: AlarmType,
    val cause: AlarmCause,
    val value: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val isAcknowledged: Boolean,
    val occurrenceCount: Int? = null
)

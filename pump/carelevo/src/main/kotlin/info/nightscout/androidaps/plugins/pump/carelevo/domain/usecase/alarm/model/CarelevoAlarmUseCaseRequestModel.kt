package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.alarm.model

import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmCause
import info.nightscout.androidaps.plugins.pump.carelevo.domain.type.AlarmType
import info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class AlarmClearUseCaseRequest(
    val alarmId: String,
    val alarmType: AlarmType,
    val alarmCause: AlarmCause,
    val address: String? = null,
    val resumeType: Int? = null,
    val resumeMode: Int? = null,
) : CarelevoUseCaseRequest
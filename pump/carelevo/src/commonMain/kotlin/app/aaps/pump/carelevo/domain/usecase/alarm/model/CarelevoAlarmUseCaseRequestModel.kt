package app.aaps.pump.carelevo.domain.usecase.alarm.model

import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest

data class AlarmClearUseCaseRequest(
    val alarmId: String,
    val alarmType: AlarmType,
    val alarmCause: AlarmCause,
    val address: String? = null,
    val resumeType: Int? = null,
    val resumeMode: Int? = null,
) : CarelevoUseCaseRequest
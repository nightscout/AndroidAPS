package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType

class AlarmClearRequestUseCase(
    private val alarmRepository: CarelevoAlarmInfoRepository
) {

    /**
     * Map an [AlarmCause]'s [AlarmType] to the wire alarm-type byte (ALERT=162, NOTICE=163) used to build
     * the `AlarmClearCommand`.
     */
    fun commandAlarmType(alarmCause: AlarmCause): Int = when (alarmCause.alarmType) {
        AlarmType.ALERT  -> ALARM_TYPE_ALERT
        AlarmType.NOTICE -> ALARM_TYPE_NOTICE
        else             -> throw IllegalArgumentException("alarmType is not supported")
    }

    /**
     * Persist the alarm acknowledgement (remove from the alarm store) after a successful
     * alarm-clear write. Returns false if the write throws.
     */
    fun persistAlarmCleared(alarmId: String): Boolean = runCatching {
        alarmRepository.removeAlarm(alarmId).blockingAwait()
    }.isSuccess

    companion object {

        private const val ALARM_TYPE_ALERT = 162
        private const val ALARM_TYPE_NOTICE = 163
    }
}

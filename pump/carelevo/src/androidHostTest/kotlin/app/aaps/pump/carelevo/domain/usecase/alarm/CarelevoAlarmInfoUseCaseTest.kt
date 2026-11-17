package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class CarelevoAlarmInfoUseCaseTest {

    private val repository: CarelevoAlarmInfoRepository = mock()
    private val sut = CarelevoAlarmInfoUseCase(repository)

    @Test
    fun observeAlarms_returns_repository_stream() {
        val alarms = listOf(sampleAlarm())
        whenever(repository.observeAlarms()).thenReturn(flowOf(alarms))

        val result = runBlocking { sut.observeAlarms().first() }

        assertThat(result).hasSize(1)
        verify(repository).observeAlarms()
    }

    @Test
    fun getAlarmsOnce_delegates_to_repository() {
        whenever(repository.getAlarmsOnce()).thenReturn(emptyList())

        val result = runBlocking { sut.getAlarmsOnce() }

        assertThat(result).isNotNull()
        verify(repository).getAlarmsOnce()
    }

    @Test
    fun upsertAlarm_delegates_to_repository() {
        val alarm = sampleAlarm()

        runBlocking { sut.upsertAlarm(alarm) }

        verify(repository).upsertAlarm(alarm)
    }

    @Test
    fun acknowledgeAlarm_removes_alarm_from_store() {
        runBlocking { sut.acknowledgeAlarm("alarm-1") }

        verify(repository).removeAlarm(eq("alarm-1"))
    }

    @Test
    fun clearAlarms_delegates_to_repository() {
        runBlocking { sut.clearAlarms() }

        verify(repository).clearAlarms()
    }

    private fun sampleAlarm(): CarelevoAlarmInfo =
        CarelevoAlarmInfo(
            alarmId = "alarm-1",
            alarmType = AlarmType.ALERT,
            cause = AlarmCause.ALARM_ALERT_LOW_BATTERY,
            value = 3,
            createdAt = "2026-03-09 09:00:00",
            updatedAt = "2026-03-09 09:00:00",
            isAcknowledged = false
        )
}

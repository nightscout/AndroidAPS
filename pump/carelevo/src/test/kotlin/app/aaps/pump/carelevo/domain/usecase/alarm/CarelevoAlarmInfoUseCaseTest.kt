package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

internal class CarelevoAlarmInfoUseCaseTest {

    private val repository: CarelevoAlarmInfoRepository = mock()
    private val sut = CarelevoAlarmInfoUseCase(repository)

    @Test
    fun observeAlarms_returns_repository_stream() {
        val alarms = listOf(sampleAlarm())
        whenever(repository.observeAlarms()).thenReturn(Observable.just(Optional.of(alarms)))

        val result = sut.observeAlarms().blockingFirst()

        assertThat(result.get()).hasSize(1)
        verify(repository).observeAlarms()
    }

    @Test
    fun getAlarmsOnce_delegates_to_repository() {
        whenever(repository.getAlarmsOnce()).thenReturn(Single.just(Optional.of(emptyList())))

        val result = sut.getAlarmsOnce().blockingGet()

        assertThat(result.isPresent).isTrue()
        verify(repository).getAlarmsOnce()
    }

    @Test
    fun upsertAlarm_delegates_to_repository() {
        val alarm = sampleAlarm()
        whenever(repository.upsertAlarm(alarm)).thenReturn(Completable.complete())

        sut.upsertAlarm(alarm).blockingAwait()

        verify(repository).upsertAlarm(alarm)
    }

    @Test
    fun acknowledgeAlarm_removes_alarm_from_store() {
        whenever(repository.removeAlarm(any())).thenReturn(Completable.complete())

        sut.acknowledgeAlarm("alarm-1").blockingAwait()

        verify(repository).removeAlarm(eq("alarm-1"))
    }

    @Test
    fun clearAlarms_delegates_to_repository() {
        whenever(repository.clearAlarms()).thenReturn(Completable.complete())

        sut.clearAlarms().blockingAwait()

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

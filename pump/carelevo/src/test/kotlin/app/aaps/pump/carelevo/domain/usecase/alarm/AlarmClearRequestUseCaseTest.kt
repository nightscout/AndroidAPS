package app.aaps.pump.carelevo.domain.usecase.alarm

import app.aaps.pump.carelevo.domain.repository.CarelevoAlarmInfoRepository
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

/**
 * Covers the wire-byte mapping and the persist-on-success seam of [AlarmClearRequestUseCase].
 * The store delegation itself lives in `CarelevoAlarmInfoUseCaseTest`.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class AlarmClearRequestUseCaseTest {

    @Mock lateinit var alarmRepository: CarelevoAlarmInfoRepository

    private lateinit var sut: AlarmClearRequestUseCase

    @BeforeEach
    fun setUp() {
        sut = AlarmClearRequestUseCase(alarmRepository)
    }

    // ---------- commandAlarmType ----------

    @Test
    fun commandAlarmType_maps_alert_cause_to_wire_byte_162() {
        assertThat(sut.commandAlarmType(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)).isEqualTo(162)
    }

    @Test
    fun commandAlarmType_maps_notice_cause_to_wire_byte_163() {
        assertThat(sut.commandAlarmType(AlarmCause.ALARM_NOTICE_LOW_INSULIN)).isEqualTo(163)
    }

    @Test
    fun commandAlarmType_maps_every_alert_cause_to_162() {
        val alertCauses = AlarmCause.entries.filter { it.alarmType == AlarmType.ALERT }

        assertThat(alertCauses).isNotEmpty()
        alertCauses.forEach { cause ->
            assertThat(sut.commandAlarmType(cause)).isEqualTo(162)
        }
    }

    @Test
    fun commandAlarmType_maps_every_notice_cause_to_163() {
        val noticeCauses = AlarmCause.entries.filter { it.alarmType == AlarmType.NOTICE }

        assertThat(noticeCauses).isNotEmpty()
        noticeCauses.forEach { cause ->
            assertThat(sut.commandAlarmType(cause)).isEqualTo(163)
        }
    }

    @Test
    fun commandAlarmType_rejects_warning_cause() {
        // WARNING is a pump-fault tier that is never cleared over this command.
        val exception = assertThrows(IllegalArgumentException::class.java) {
            sut.commandAlarmType(AlarmCause.ALARM_WARNING_LOW_INSULIN)
        }

        assertThat(exception).hasMessageThat().isEqualTo("alarmType is not supported")
    }

    @Test
    fun commandAlarmType_rejects_every_warning_cause() {
        val warningCauses = AlarmCause.entries.filter { it.alarmType == AlarmType.WARNING }

        assertThat(warningCauses).isNotEmpty()
        warningCauses.forEach { cause ->
            assertThrows(IllegalArgumentException::class.java) { sut.commandAlarmType(cause) }
        }
    }

    @Test
    fun commandAlarmType_rejects_unknown_cause() {
        assertThrows(IllegalArgumentException::class.java) {
            sut.commandAlarmType(AlarmCause.ALARM_UNKNOWN)
        }
    }

    @Test
    fun commandAlarmType_does_not_touch_the_alarm_store() {
        sut.commandAlarmType(AlarmCause.ALARM_ALERT_LOW_BATTERY)
        sut.commandAlarmType(AlarmCause.ALARM_NOTICE_BG_CHECK)

        verifyNoMoreInteractions(alarmRepository)
    }

    // ---------- persistAlarmCleared ----------

    @Test
    fun persistAlarmCleared_removes_alarm_and_returns_true() {
        whenever(alarmRepository.removeAlarm(eq("alarm-1"))).thenReturn(Completable.complete())

        assertThat(sut.persistAlarmCleared("alarm-1")).isTrue()

        verify(alarmRepository).removeAlarm(eq("alarm-1"))
    }

    @Test
    fun persistAlarmCleared_returns_false_when_removal_errors() {
        whenever(alarmRepository.removeAlarm(eq("alarm-1")))
            .thenReturn(Completable.error(RuntimeException("db down")))

        assertThat(sut.persistAlarmCleared("alarm-1")).isFalse()

        verify(alarmRepository).removeAlarm(eq("alarm-1"))
    }

    @Test
    fun persistAlarmCleared_returns_false_when_repository_throws_synchronously() {
        whenever(alarmRepository.removeAlarm(eq("alarm-1"))).thenThrow(IllegalStateException("boom"))

        assertThat(sut.persistAlarmCleared("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmCleared_returns_false_on_checked_style_error_without_rethrowing() {
        whenever(alarmRepository.removeAlarm(eq("alarm-1")))
            .thenReturn(Completable.error(IOException("io")))

        // runCatching swallows the wrapped checked exception rather than escaping to the caller.
        assertThat(sut.persistAlarmCleared("alarm-1")).isFalse()
    }

    @Test
    fun persistAlarmCleared_only_removes_the_requested_alarm() {
        whenever(alarmRepository.removeAlarm(eq("alarm-7"))).thenReturn(Completable.complete())

        sut.persistAlarmCleared("alarm-7")

        verify(alarmRepository).removeAlarm(eq("alarm-7"))
        verify(alarmRepository, never()).clearAlarms()
        verifyNoMoreInteractions(alarmRepository)
    }
}

package app.aaps.pump.carelevo.domain.usecase.alarm.model

import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Value semantics of the [AlarmClearUseCaseRequest] carrier: defaults, copy and equality. */
internal class AlarmClearUseCaseRequestTest {

    private fun request(): AlarmClearUseCaseRequest =
        AlarmClearUseCaseRequest(
            alarmId = "alarm-1",
            alarmType = AlarmType.ALERT,
            alarmCause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN
        )

    @Test
    fun optional_resume_and_address_fields_default_to_null() {
        with(request()) {
            assertThat(address).isNull()
            assertThat(resumeType).isNull()
            assertThat(resumeMode).isNull()
        }
    }

    @Test
    fun required_fields_are_carried_verbatim() {
        with(request()) {
            assertThat(alarmId).isEqualTo("alarm-1")
            assertThat(alarmType).isEqualTo(AlarmType.ALERT)
            assertThat(alarmCause).isEqualTo(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)
        }
    }

    @Test
    fun resume_fields_are_carried_when_supplied() {
        val sut = AlarmClearUseCaseRequest(
            alarmId = "alarm-2",
            alarmType = AlarmType.NOTICE,
            alarmCause = AlarmCause.ALARM_NOTICE_LGS_START,
            address = "AA:BB:CC:DD:EE:FF",
            resumeType = 1,
            resumeMode = 2
        )

        with(sut) {
            assertThat(address).isEqualTo("AA:BB:CC:DD:EE:FF")
            assertThat(resumeType).isEqualTo(1)
            assertThat(resumeMode).isEqualTo(2)
        }
    }

    @Test
    fun is_a_use_case_request() {
        assertThat(request()).isInstanceOf(CarelevoUseCaseRequest::class.java)
    }

    @Test
    fun equal_payloads_are_equal_and_share_a_hash_code() {
        assertThat(request()).isEqualTo(request())
        assertThat(request().hashCode()).isEqualTo(request().hashCode())
    }

    @Test
    fun requests_differing_in_alarm_id_are_not_equal() {
        assertThat(request().copy(alarmId = "alarm-9")).isNotEqualTo(request())
    }

    @Test
    fun requests_differing_in_resume_mode_are_not_equal() {
        assertThat(request().copy(resumeMode = 4)).isNotEqualTo(request())
    }

    @Test
    fun copy_overrides_only_the_named_field() {
        val copy = request().copy(address = "11:22:33:44:55:66")

        with(copy) {
            assertThat(address).isEqualTo("11:22:33:44:55:66")
            assertThat(alarmId).isEqualTo("alarm-1")
            assertThat(alarmType).isEqualTo(AlarmType.ALERT)
            assertThat(alarmCause).isEqualTo(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)
            assertThat(resumeType).isNull()
            assertThat(resumeMode).isNull()
        }
    }

    @Test
    fun toString_exposes_the_alarm_identity() {
        assertThat(request().toString()).contains("alarm-1")
        assertThat(request().toString()).contains("ALARM_ALERT_OUT_OF_INSULIN")
    }
}

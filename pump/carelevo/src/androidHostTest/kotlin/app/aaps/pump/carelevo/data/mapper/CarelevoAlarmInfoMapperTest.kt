package app.aaps.pump.carelevo.data.mapper

import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure-logic unit tests for the entity <-> domain mappers in `CarelevoAlarmInfoMapper.kt`.
 *
 * Covers both directions, the `Int` <-> [AlarmType] code translation for every tier (including the
 * unknown-code fallback), the nullable `value` branch, and the deliberate `occurrenceCount` loss on
 * the domain -> entity path (the mapper never forwards it, so the entity default of 1 is used).
 */
internal class CarelevoAlarmInfoMapperTest {

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    // region entity -> domain

    @Test
    fun `entity to domain maps every scalar field`() {
        val entity = CarelevoAlarmInfoEntity(
            alarmId = "a-1", alarmType = 0, cause = AlarmCause.ALARM_WARNING_LOW_INSULIN,
            value = 12, createdAt = created, updatedAt = updated, acknowledged = true, occurrenceCount = 4
        )
        val domain = entity.transformToDomainModel()
        assertThat(domain.alarmId).isEqualTo("a-1")
        assertThat(domain.alarmType).isEqualTo(AlarmType.WARNING)
        assertThat(domain.cause).isEqualTo(AlarmCause.ALARM_WARNING_LOW_INSULIN)
        assertThat(domain.value).isEqualTo(12)
        assertThat(domain.createdAt).isEqualTo(created)
        assertThat(domain.updatedAt).isEqualTo(updated)
        assertThat(domain.isAcknowledged).isTrue()
        assertThat(domain.occurrenceCount).isEqualTo(4)
    }

    @Test
    fun `entity to domain maps null value`() {
        val entity = CarelevoAlarmInfoEntity(
            "a-2", 2, AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = null, createdAt = created,
            updatedAt = updated, acknowledged = false
        )
        val domain = entity.transformToDomainModel()
        assertThat(domain.value).isNull()
        assertThat(domain.isAcknowledged).isFalse()
    }

    @Test
    fun `entity to domain applies default occurrenceCount of one`() {
        val entity = CarelevoAlarmInfoEntity(
            "a-3", 1, AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, value = 0, createdAt = created,
            updatedAt = updated, acknowledged = false
        )
        assertThat(entity.transformToDomainModel().occurrenceCount).isEqualTo(1)
    }

    @Test
    fun `entity to domain maps alarmType code 0 to WARNING`() {
        assertThat(alarmEntityWithType(0).transformToDomainModel().alarmType).isEqualTo(AlarmType.WARNING)
    }

    @Test
    fun `entity to domain maps alarmType code 1 to ALERT`() {
        assertThat(alarmEntityWithType(1).transformToDomainModel().alarmType).isEqualTo(AlarmType.ALERT)
    }

    @Test
    fun `entity to domain maps alarmType code 2 to NOTICE`() {
        assertThat(alarmEntityWithType(2).transformToDomainModel().alarmType).isEqualTo(AlarmType.NOTICE)
    }

    @Test
    fun `entity to domain maps alarmType code 3 to UNKNOWN_TYPE`() {
        assertThat(alarmEntityWithType(3).transformToDomainModel().alarmType).isEqualTo(AlarmType.UNKNOWN_TYPE)
    }

    @Test
    fun `entity to domain maps unrecognized alarmType code to UNKNOWN_TYPE`() {
        assertThat(alarmEntityWithType(42).transformToDomainModel().alarmType).isEqualTo(AlarmType.UNKNOWN_TYPE)
    }

    @Test
    fun `entity to domain maps negative alarmType code to UNKNOWN_TYPE`() {
        assertThat(alarmEntityWithType(-1).transformToDomainModel().alarmType).isEqualTo(AlarmType.UNKNOWN_TYPE)
    }

    // endregion

    // region domain -> entity

    @Test
    fun `domain to entity maps every forwarded field`() {
        val domain = CarelevoAlarmInfo(
            alarmId = "d-1", alarmType = AlarmType.ALERT, cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            value = 7, createdAt = created, updatedAt = updated, isAcknowledged = true, occurrenceCount = 9
        )
        val entity = domain.transformToEntity()
        assertThat(entity.alarmId).isEqualTo("d-1")
        assertThat(entity.alarmType).isEqualTo(1)
        assertThat(entity.cause).isEqualTo(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)
        assertThat(entity.value).isEqualTo(7)
        assertThat(entity.createdAt).isEqualTo(created)
        assertThat(entity.updatedAt).isEqualTo(updated)
        assertThat(entity.acknowledged).isTrue()
    }

    @Test
    fun `domain to entity maps null value`() {
        val domain = CarelevoAlarmInfo(
            "d-2", AlarmType.WARNING, AlarmCause.ALARM_WARNING_LOW_INSULIN, value = null,
            createdAt = created, updatedAt = updated, isAcknowledged = false
        )
        val entity = domain.transformToEntity()
        assertThat(entity.value).isNull()
        assertThat(entity.acknowledged).isFalse()
    }

    @Test
    fun `domain to entity does not forward occurrenceCount so entity default of one is used`() {
        val domain = CarelevoAlarmInfo(
            "d-3", AlarmType.NOTICE, AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = null,
            createdAt = created, updatedAt = updated, isAcknowledged = false, occurrenceCount = 5
        )
        assertThat(domain.transformToEntity().occurrenceCount).isEqualTo(1)
    }

    @Test
    fun `domain to entity maps WARNING to code 0`() {
        assertThat(alarmDomainWithType(AlarmType.WARNING).transformToEntity().alarmType).isEqualTo(0)
    }

    @Test
    fun `domain to entity maps ALERT to code 1`() {
        assertThat(alarmDomainWithType(AlarmType.ALERT).transformToEntity().alarmType).isEqualTo(1)
    }

    @Test
    fun `domain to entity maps NOTICE to code 2`() {
        assertThat(alarmDomainWithType(AlarmType.NOTICE).transformToEntity().alarmType).isEqualTo(2)
    }

    @Test
    fun `domain to entity maps UNKNOWN_TYPE to code 3`() {
        assertThat(alarmDomainWithType(AlarmType.UNKNOWN_TYPE).transformToEntity().alarmType).isEqualTo(3)
    }

    // endregion

    // region round trips

    @Test
    fun `entity round trips through domain when occurrenceCount is one`() {
        val entity = CarelevoAlarmInfoEntity(
            "r-1", 1, AlarmCause.ALARM_ALERT_LOW_BATTERY, value = 3, createdAt = created,
            updatedAt = updated, acknowledged = true, occurrenceCount = 1
        )
        assertThat(entity.transformToDomainModel().transformToEntity()).isEqualTo(entity)
    }

    @Test
    fun `entity round trip collapses occurrenceCount above one to the default`() {
        val entity = CarelevoAlarmInfoEntity(
            "r-2", 0, AlarmCause.ALARM_WARNING_PUMP_CLOGGED, value = null, createdAt = created,
            updatedAt = updated, acknowledged = false, occurrenceCount = 3
        )
        val back = entity.transformToDomainModel().transformToEntity()
        assertThat(back.occurrenceCount).isEqualTo(1)
        assertThat(back).isEqualTo(entity.copy(occurrenceCount = 1))
    }

    @Test
    fun `domain round trips through entity when tier is known and occurrenceCount is one`() {
        val domain = CarelevoAlarmInfo(
            "r-3", AlarmType.NOTICE, AlarmCause.ALARM_NOTICE_BG_CHECK, value = 5, createdAt = created,
            updatedAt = updated, isAcknowledged = true, occurrenceCount = 1
        )
        assertThat(domain.transformToEntity().transformToDomainModel()).isEqualTo(domain)
    }

    // endregion

    private fun alarmEntityWithType(type: Int) = CarelevoAlarmInfoEntity(
        alarmId = "x", alarmType = type, cause = AlarmCause.ALARM_UNKNOWN, value = null,
        createdAt = created, updatedAt = updated, acknowledged = false
    )

    private fun alarmDomainWithType(type: AlarmType) = CarelevoAlarmInfo(
        alarmId = "x", alarmType = type, cause = AlarmCause.ALARM_UNKNOWN, value = null,
        createdAt = created, updatedAt = updated, isAcknowledged = false
    )
}

package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Tests for [CarelevoAlarmInfoLocalRepositoryImpl] — delegates to
 * [CarelevoAlarmInfoLocalDataSource] and applies the entity <-> domain mappers. Covers the
 * present/null list branches and the domain->entity mapping on the write paths.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoAlarmInfoLocalRepositoryImplTest {

    @Mock lateinit var dataSource: CarelevoAlarmInfoLocalDataSource

    private lateinit var sut: CarelevoAlarmInfoLocalRepositoryImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoAlarmInfoLocalRepositoryImpl(dataSource)
    }

    private fun entity(id: String = "a-1") = CarelevoAlarmInfoEntity(
        alarmId = id, alarmType = 1, cause = AlarmCause.ALARM_ALERT_LOW_BATTERY, value = 3,
        createdAt = created, updatedAt = updated, acknowledged = false
    )

    private fun domain(id: String = "a-1") = CarelevoAlarmInfo(
        alarmId = id, alarmType = AlarmType.ALERT, cause = AlarmCause.ALARM_ALERT_LOW_BATTERY,
        value = 3, createdAt = created, updatedAt = updated, isAcknowledged = false
    )

    @Test
    fun `observeAlarms maps each entity to a domain model`() {
        whenever(dataSource.observeAlarms()).thenReturn(flowOf(listOf(entity())))

        val result = runBlocking { sut.observeAlarms().first() }

        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        val alarm = result.first()
        assertThat(alarm.alarmId).isEqualTo("a-1")
        assertThat(alarm.alarmType).isEqualTo(AlarmType.ALERT)
        assertThat(alarm.cause).isEqualTo(AlarmCause.ALARM_ALERT_LOW_BATTERY)
        verify(dataSource).observeAlarms()
    }

    @Test
    fun `observeAlarms maps a null list to an empty list`() {
        whenever(dataSource.observeAlarms()).thenReturn(flowOf(null))

        val result = runBlocking { sut.observeAlarms().first() }

        assertThat(result).isNotNull()
        assertThat(result).isEmpty()
    }

    @Test
    fun `getAlarmsOnce maps each entity to a domain model`() {
        whenever(dataSource.getAlarmsOnce())
            .thenReturn(listOf(entity("a-1"), entity("a-2")))

        val result = sut.getAlarmsOnce()

        assertThat(result).isNotNull()
        assertThat(result.map { it.alarmId }).containsExactly("a-1", "a-2").inOrder()
        verify(dataSource).getAlarmsOnce()
    }

    @Test
    fun `getAlarmsOnce maps an empty list to an empty list`() {
        whenever(dataSource.getAlarmsOnce()).thenReturn(emptyList())

        assertThat(sut.getAlarmsOnce()).isEmpty()
    }

    @Test
    fun `setAlarms maps the domain list to entities and forwards it`() {
        sut.setAlarms(listOf(domain("a-1"), domain("a-2")))

        val captor = argumentCaptor<List<CarelevoAlarmInfoEntity>>()
        verify(dataSource).setAlarms(captor.capture())
        assertThat(captor.firstValue.map { it.alarmId }).containsExactly("a-1", "a-2").inOrder()
        assertThat(captor.firstValue.first().alarmType).isEqualTo(1) // ALERT -> code 1
    }

    @Test
    fun `upsertAlarm maps the domain to an entity and forwards it`() {
        sut.upsertAlarm(domain("a-7"))

        val captor = argumentCaptor<CarelevoAlarmInfoEntity>()
        verify(dataSource).upsertAlarm(captor.capture())
        assertThat(captor.firstValue.alarmId).isEqualTo("a-7")
        assertThat(captor.firstValue.alarmType).isEqualTo(1)
        assertThat(captor.firstValue.cause).isEqualTo(AlarmCause.ALARM_ALERT_LOW_BATTERY)
    }

    @Test
    fun `removeAlarm delegates the id to the data source`() {
        sut.removeAlarm("a-3")

        verify(dataSource).removeAlarm(eq("a-3"))
    }

    @Test
    fun `clearAlarms delegates to the data source`() {
        sut.clearAlarms()

        verify(dataSource).clearAlarms()
    }
}

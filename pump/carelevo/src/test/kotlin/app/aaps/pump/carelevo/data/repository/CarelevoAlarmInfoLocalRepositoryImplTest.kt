package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoAlarmInfoLocalDataSource
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import app.aaps.pump.carelevo.domain.type.AlarmType
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

/**
 * Tests for [CarelevoAlarmInfoLocalRepositoryImpl] — delegates to
 * [CarelevoAlarmInfoLocalDataSource] and applies the entity <-> domain mappers. Covers the
 * present/empty optional branches and the domain->entity mapping on the write paths.
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
        whenever(dataSource.observeAlarms()).thenReturn(Observable.just(Optional.of(listOf(entity()))))

        val result = sut.observeAlarms().blockingFirst()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get()).hasSize(1)
        val alarm = result.get().first()
        assertThat(alarm.alarmId).isEqualTo("a-1")
        assertThat(alarm.alarmType).isEqualTo(AlarmType.ALERT)
        assertThat(alarm.cause).isEqualTo(AlarmCause.ALARM_ALERT_LOW_BATTERY)
        verify(dataSource).observeAlarms()
    }

    @Test
    fun `observeAlarms maps an empty optional to an empty list`() {
        whenever(dataSource.observeAlarms()).thenReturn(Observable.just(Optional.empty<List<CarelevoAlarmInfoEntity>>()))

        val result = sut.observeAlarms().blockingFirst()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get()).isEmpty()
    }

    @Test
    fun `getAlarmsOnce maps each entity to a domain model`() {
        whenever(dataSource.getAlarmsOnce())
            .thenReturn(Single.just(Optional.of(listOf(entity("a-1"), entity("a-2")))))

        val result = sut.getAlarmsOnce().blockingGet()

        assertThat(result.isPresent).isTrue()
        assertThat(result.get().map { it.alarmId }).containsExactly("a-1", "a-2").inOrder()
        verify(dataSource).getAlarmsOnce()
    }

    @Test
    fun `getAlarmsOnce maps an empty optional to an empty list`() {
        whenever(dataSource.getAlarmsOnce()).thenReturn(Single.just(Optional.empty<List<CarelevoAlarmInfoEntity>>()))

        assertThat(sut.getAlarmsOnce().blockingGet().get()).isEmpty()
    }

    @Test
    fun `setAlarms maps the domain list to entities and forwards it`() {
        whenever(dataSource.setAlarms(any())).thenReturn(Completable.complete())

        sut.setAlarms(listOf(domain("a-1"), domain("a-2"))).blockingAwait()

        val captor = argumentCaptor<List<CarelevoAlarmInfoEntity>>()
        verify(dataSource).setAlarms(captor.capture())
        assertThat(captor.firstValue.map { it.alarmId }).containsExactly("a-1", "a-2").inOrder()
        assertThat(captor.firstValue.first().alarmType).isEqualTo(1) // ALERT -> code 1
    }

    @Test
    fun `upsertAlarm maps the domain to an entity and forwards it`() {
        whenever(dataSource.upsertAlarm(any())).thenReturn(Completable.complete())

        sut.upsertAlarm(domain("a-7")).blockingAwait()

        val captor = argumentCaptor<CarelevoAlarmInfoEntity>()
        verify(dataSource).upsertAlarm(captor.capture())
        assertThat(captor.firstValue.alarmId).isEqualTo("a-7")
        assertThat(captor.firstValue.alarmType).isEqualTo(1)
        assertThat(captor.firstValue.cause).isEqualTo(AlarmCause.ALARM_ALERT_LOW_BATTERY)
    }

    @Test
    fun `removeAlarm delegates the id to the data source`() {
        whenever(dataSource.removeAlarm("a-3")).thenReturn(Completable.complete())

        sut.removeAlarm("a-3").blockingAwait()

        verify(dataSource).removeAlarm(eq("a-3"))
    }

    @Test
    fun `clearAlarms delegates to the data source`() {
        whenever(dataSource.clearAlarms()).thenReturn(Completable.complete())

        sut.clearAlarms().blockingAwait()

        verify(dataSource).clearAlarms()
    }
}

package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoAlarmInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.domain.type.AlarmCause
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Optional

/**
 * Delegation tests for [CarelevoAlarmInfoLocalDataSourceImpl] — a thin pass-through over
 * [CarelevoAlarmInfoDao]. Each public method must forward to the matching DAO method and return the
 * DAO's own stream unmodified.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoAlarmInfoLocalDataSourceImplTest {

    @Mock lateinit var dao: CarelevoAlarmInfoDao

    private lateinit var sut: CarelevoAlarmInfoLocalDataSourceImpl

    private val created = "2026-07-16T12:00:00.000Z"
    private val updated = "2026-07-16T12:05:00.000Z"

    @BeforeEach
    fun setUp() {
        sut = CarelevoAlarmInfoLocalDataSourceImpl(dao)
    }

    private fun sampleEntity(id: String = "a-1") = CarelevoAlarmInfoEntity(
        alarmId = id, alarmType = 1, cause = AlarmCause.ALARM_ALERT_LOW_BATTERY, value = 3,
        createdAt = created, updatedAt = updated, acknowledged = false
    )

    @Test
    fun `observeAlarms returns the dao stream unchanged`() {
        val stream = Observable.just(Optional.of(listOf(sampleEntity())))
        whenever(dao.getAlarms()).thenReturn(stream)

        assertThat(sut.observeAlarms()).isSameInstanceAs(stream)
        verify(dao).getAlarms()
    }

    @Test
    fun `observeAlarms emits the dao value`() {
        whenever(dao.getAlarms()).thenReturn(Observable.just(Optional.of(listOf(sampleEntity()))))

        val emitted = sut.observeAlarms().blockingFirst()

        assertThat(emitted.get()).hasSize(1)
        assertThat(emitted.get().first().alarmId).isEqualTo("a-1")
    }

    @Test
    fun `getAlarmsOnce returns the dao single unchanged`() {
        val single = Single.just(Optional.of(listOf(sampleEntity())))
        whenever(dao.getAlarmsOnce()).thenReturn(single)

        assertThat(sut.getAlarmsOnce()).isSameInstanceAs(single)
        verify(dao).getAlarmsOnce()
    }

    @Test
    fun `getAlarmsOnce carries an empty optional through`() {
        whenever(dao.getAlarmsOnce()).thenReturn(Single.just(Optional.empty<List<CarelevoAlarmInfoEntity>>()))

        assertThat(sut.getAlarmsOnce().blockingGet().isPresent).isFalse()
    }

    @Test
    fun `setAlarms forwards the list to the dao`() {
        val list = listOf(sampleEntity("a-1"), sampleEntity("a-2"))
        val completable = Completable.complete()
        whenever(dao.setAlarms(list)).thenReturn(completable)

        assertThat(sut.setAlarms(list)).isSameInstanceAs(completable)
        verify(dao).setAlarms(eq(list))
    }

    @Test
    fun `upsertAlarm forwards the entity to the dao`() {
        val entity = sampleEntity()
        val completable = Completable.complete()
        whenever(dao.upsertAlarm(entity)).thenReturn(completable)

        assertThat(sut.upsertAlarm(entity)).isSameInstanceAs(completable)
        verify(dao).upsertAlarm(eq(entity))
    }

    @Test
    fun `removeAlarm forwards the id to the dao`() {
        val completable = Completable.complete()
        whenever(dao.removeAlarm("a-9")).thenReturn(completable)

        assertThat(sut.removeAlarm("a-9")).isSameInstanceAs(completable)
        verify(dao).removeAlarm(eq("a-9"))
    }

    @Test
    fun `clearAlarms delegates to the dao`() {
        val completable = Completable.complete()
        whenever(dao.clearAlarms()).thenReturn(completable)

        assertThat(sut.clearAlarms()).isSameInstanceAs(completable)
        verify(dao).clearAlarms()
    }
}

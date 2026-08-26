package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.config.PrefEnvConfig
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoAlarmInfoEntity
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Unit coverage for [CarelevoAlarmInfoDaoImpl].
 *
 * The DAO persists the alarm list as a Gson-serialized JSON string in [SP] under a single key. The
 * mocked [SP] is driven as an in-memory fake — a backing map answers `getString`/`putString`/`remove`
 * — so every method exercises the *real* [CarelevoGsonHelper] serialization round trip.
 *
 * The load path is deliberately UNFILTERED: everything in the store is an active (unacknowledged)
 * alarm by construction because acknowledging an alarm removes it (see `removeAlarm`). The regression
 * these tests guard against is the old vendor code filtering the cold-load stream to `it.acknowledged`
 * — which is never true — silently dropping every persisted active alarm on process restart.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoAlarmInfoDaoImplTest {

    @Mock lateinit var prefManager: SP

    private val key = PrefEnvConfig.CARELEVO_ALARM_INFO_LIST
    private val store = mutableMapOf<String, String>()

    private lateinit var sut: CarelevoAlarmInfoDaoImpl

    @BeforeEach
    fun setUp() {
        // Fake shared-preferences: a backing map behind the three String-keyed methods the DAO uses.
        // any<String>() picks the String overload over the @StringRes Int overload of each.
        whenever(prefManager.getString(any<String>(), any<String>())).thenAnswer {
            val k = it.getArgument<String>(0)
            val default = it.getArgument<String>(1)
            store[k] ?: default
        }
        doAnswer {
            store[it.getArgument<String>(0)] = it.getArgument<String>(1)
            null
        }.whenever(prefManager).putString(any<String>(), any<String>())
        doAnswer {
            store.remove(it.getArgument<String>(0))
            null
        }.whenever(prefManager).remove(any<String>())

        sut = CarelevoAlarmInfoDaoImpl(prefManager)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun entity(
        alarmId: String = "alarm-1",
        cause: AlarmCause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
        alarmType: Int = cause.alarmType.code,
        value: Int? = null,
        acknowledged: Boolean = false,
        occurrenceCount: Int = 1,
        createdAt: String = "2026-07-16T10:00:00",
        updatedAt: String = "2026-07-16T10:00:00"
    ): CarelevoAlarmInfoEntity = CarelevoAlarmInfoEntity(
        alarmId = alarmId,
        alarmType = alarmType,
        cause = cause,
        value = value,
        createdAt = createdAt,
        updatedAt = updatedAt,
        acknowledged = acknowledged,
        occurrenceCount = occurrenceCount
    )

    /** Seed the persisted store as a prior process would have — through the real Gson helper. */
    private fun seedStore(vararg alarms: CarelevoAlarmInfoEntity) {
        store[key] = CarelevoGsonHelper.sharedGson().toJson(alarms.toList())
    }

    // ---------------------------------------------------------------------------------------------
    // getAlarms — cold load
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `getAlarms cold-loads an empty list when nothing is persisted`() {
        val result = sut.getAlarms().blockingFirst()
        assertThat(result.isPresent).isTrue()
        assertThat(result.get()).isEmpty()
    }

    @Test
    fun `getAlarms cold-loads every persisted alarm UNFILTERED including acknowledged ones`() {
        val active = entity(alarmId = "active", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, acknowledged = false)
        val acked = entity(alarmId = "acked", cause = AlarmCause.ALARM_WARNING_PUMP_CLOGGED, acknowledged = true)
        seedStore(active, acked)

        val result = sut.getAlarms().blockingFirst()

        assertThat(result.isPresent).isTrue()
        // The regression guard: BOTH alarms survive the cold load, not just the (never-true) acknowledged one.
        assertThat(result.get()).containsExactly(active, acked)
    }

    @Test
    fun `getAlarms emits Optional-empty and does not crash when the persisted JSON is unparseable`() {
        store[key] = "{}" // an object where an array is expected → Gson throws, runCatching swallows it

        val result = sut.getAlarms().blockingFirst()

        assertThat(result.isPresent).isFalse()
    }

    @Test
    fun `getAlarms warms the cache and does not re-read preferences on the second call`() {
        seedStore(entity())
        assertThat(sut.getAlarms().blockingFirst().get()).hasSize(1)

        store[key] = "{}" // corrupt the store; a second read would blow up if it re-read
        val second = sut.getAlarms().blockingFirst()

        assertThat(second.get()).hasSize(1)
        verify(prefManager, times(1)).getString(any<String>(), any<String>())
    }

    @Test
    fun `getAlarms returns the live subject and emits subsequent upserts to existing subscribers`() {
        val observer = sut.getAlarms().test()

        sut.upsertAlarm(entity()).blockingAwait()

        observer.assertValueCount(2) // initial cold-load empty list + post-upsert list
        assertThat(observer.values().last().get()).hasSize(1)
    }

    @Test
    fun `getAlarms does not reload after clearAlarms even when the store is re-seeded`() {
        seedStore(entity())
        sut.getAlarms().blockingFirst()
        sut.clearAlarms().blockingAwait() // value becomes Optional-empty (non-null) → guard stays false

        seedStore(entity("a1"), entity("a2"))
        val after = sut.getAlarms().blockingFirst()

        assertThat(after.isPresent).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // getAlarmsOnce
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `getAlarmsOnce cold-loads an empty list when nothing is persisted`() {
        val result = sut.getAlarmsOnce().blockingGet()
        assertThat(result.isPresent).isTrue()
        assertThat(result.get()).isEmpty()
    }

    @Test
    fun `getAlarmsOnce returns all stored alarms unfiltered`() {
        val a = entity(alarmId = "a", cause = AlarmCause.ALARM_WARNING_LOW_INSULIN, acknowledged = false)
        val b = entity(alarmId = "b", cause = AlarmCause.ALARM_NOTICE_LGS_START, acknowledged = true)
        seedStore(a, b)

        val result = sut.getAlarmsOnce().blockingGet()

        assertThat(result.get()).containsExactly(a, b)
    }

    @Test
    fun `getAlarmsOnce uses the warm cache after a prior setAlarms`() {
        val list = listOf(entity())
        sut.setAlarms(list).blockingAwait()

        store[key] = "{}" // would fail to parse if the cache were bypassed
        val result = sut.getAlarmsOnce().blockingGet()

        assertThat(result.get()).isEqualTo(list)
    }

    @Test
    fun `getAlarmsOnce surfaces the parse error when the persisted JSON is unparseable`() {
        store[key] = "{}"

        sut.getAlarmsOnce().test().assertError(RuntimeException::class.java)
    }

    @Test
    fun `getAlarmsOnce reloads from the store after clearAlarms clears the cache to empty`() {
        seedStore(entity())
        sut.getAlarms().blockingFirst()
        sut.clearAlarms().blockingAwait() // key removed, cache Optional-empty → ensureLoaded re-reads store

        val result = sut.getAlarmsOnce().blockingGet()

        assertThat(result.get()).isEmpty()
    }

    // ---------------------------------------------------------------------------------------------
    // setAlarms
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `setAlarms persists the list as JSON and pushes it onto the stream`() {
        val list = listOf(entity(alarmId = "x"), entity(alarmId = "y", cause = AlarmCause.ALARM_WARNING_LOW_BATTERY))

        sut.setAlarms(list).blockingAwait()

        verify(prefManager).putString(eq(key), any<String>())
        assertThat(store).containsKey(key)
        assertThat(sut.getAlarms().blockingFirst().get()).containsExactlyElementsIn(list)
    }

    @Test
    fun `setAlarms overwrites the previously persisted content`() {
        sut.setAlarms(listOf(entity(alarmId = "first"))).blockingAwait()
        sut.setAlarms(listOf(entity(alarmId = "second"))).blockingAwait()

        val reloaded = CarelevoAlarmInfoDaoImpl(prefManager).getAlarmsOnce().blockingGet()
        assertThat(reloaded.get().map { it.alarmId }).containsExactly("second")
    }

    // ---------------------------------------------------------------------------------------------
    // clearAlarms
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `clearAlarms removes the preference key and emits Optional-empty`() {
        seedStore(entity())
        sut.getAlarms().blockingFirst() // warm the cache first

        sut.clearAlarms().blockingAwait()

        verify(prefManager).remove(eq(key))
        assertThat(store).doesNotContainKey(key)
        assertThat(sut.getAlarms().blockingFirst().isPresent).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // upsertAlarm
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `upsertAlarm appends a brand new alarm and forces its occurrenceCount to one`() {
        // occurrenceCount passed in (7) must be normalized to 1 for a first-seen alarm.
        sut.upsertAlarm(entity(alarmId = "new", occurrenceCount = 7)).blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored).hasSize(1)
        assertThat(stored.single().occurrenceCount).isEqualTo(1)
    }

    @Test
    fun `upsertAlarm increments occurrenceCount and refreshes updatedAt on an existing match`() {
        val existing = entity(
            alarmId = "existing",
            cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN,
            acknowledged = false,
            occurrenceCount = 1,
            createdAt = "2026-07-16T09:00:00",
            updatedAt = "2026-07-16T09:00:00"
        )
        seedStore(existing)
        sut.getAlarms().blockingFirst()

        // Same alarmType + cause + unacknowledged, but a different id/updatedAt.
        sut.upsertAlarm(
            entity(alarmId = "incoming", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, updatedAt = "2026-07-16T11:00:00")
        ).blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored).hasSize(1)
        val merged = stored.single()
        assertThat(merged.occurrenceCount).isEqualTo(2)
        assertThat(merged.updatedAt).isEqualTo("2026-07-16T11:00:00")
        // Identity of the existing row is preserved (copy, not replace).
        assertThat(merged.alarmId).isEqualTo("existing")
        assertThat(merged.createdAt).isEqualTo("2026-07-16T09:00:00")
    }

    @Test
    fun `upsertAlarm appends instead of merging when the only match is acknowledged`() {
        seedStore(entity(alarmId = "acked", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, acknowledged = true))
        sut.getAlarms().blockingFirst()

        sut.upsertAlarm(entity(alarmId = "fresh", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)).blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored).hasSize(2)
    }

    @Test
    fun `upsertAlarm appends when the cause differs from every stored alarm`() {
        seedStore(entity(alarmId = "one", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN))
        sut.getAlarms().blockingFirst()

        sut.upsertAlarm(entity(alarmId = "two", cause = AlarmCause.ALARM_ALERT_LOW_BATTERY)).blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored).hasSize(2)
        assertThat(stored.map { it.cause }).containsExactly(
            AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, AlarmCause.ALARM_ALERT_LOW_BATTERY
        )
    }

    @Test
    fun `upsertAlarm appends when the alarmType differs despite an identical cause`() {
        // Same cause, but a deliberately mismatched alarmType exercises the alarmType leg of the match.
        seedStore(entity(alarmId = "weird", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, alarmType = 999))
        sut.getAlarms().blockingFirst()

        sut.upsertAlarm(entity(alarmId = "normal", cause = AlarmCause.ALARM_ALERT_OUT_OF_INSULIN)).blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored).hasSize(2)
    }

    @Test
    fun `upsertAlarm keeps incrementing occurrenceCount across repeated calls`() {
        val alarm = entity(alarmId = "repeat", cause = AlarmCause.ALARM_WARNING_LOW_INSULIN)
        sut.upsertAlarm(alarm).blockingAwait()
        sut.upsertAlarm(alarm).blockingAwait()
        sut.upsertAlarm(alarm).blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored).hasSize(1)
        assertThat(stored.single().occurrenceCount).isEqualTo(3)
    }

    // ---------------------------------------------------------------------------------------------
    // removeAlarm
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `removeAlarm drops the matching id and persists the remainder`() {
        seedStore(entity(alarmId = "keep"), entity(alarmId = "drop", cause = AlarmCause.ALARM_WARNING_LOW_BATTERY))
        sut.getAlarms().blockingFirst()

        sut.removeAlarm("drop").blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored.map { it.alarmId }).containsExactly("keep")
        // Persisted too: a fresh cold read agrees.
        val reloaded = CarelevoAlarmInfoDaoImpl(prefManager).getAlarmsOnce().blockingGet()
        assertThat(reloaded.get().map { it.alarmId }).containsExactly("keep")
    }

    @Test
    fun `removeAlarm with an unknown id leaves the list intact but still persists and emits`() {
        seedStore(entity(alarmId = "a"), entity(alarmId = "b", cause = AlarmCause.ALARM_WARNING_LOW_BATTERY))
        sut.getAlarms().blockingFirst()

        sut.removeAlarm("does-not-exist").blockingAwait()

        val stored = sut.getAlarms().blockingFirst().get()
        assertThat(stored.map { it.alarmId }).containsExactly("a", "b")
        verify(prefManager, times(1)).putString(eq(key), any<String>())
    }

    @Test
    fun `removeAlarm emits the updated list to existing stream subscribers`() {
        seedStore(entity(alarmId = "a"), entity(alarmId = "b", cause = AlarmCause.ALARM_WARNING_LOW_BATTERY))
        val observer = sut.getAlarms().test()

        sut.removeAlarm("a").blockingAwait()

        assertThat(observer.values().last().get().map { it.alarmId }).containsExactly("b")
    }

    // ---------------------------------------------------------------------------------------------
    // End-to-end serialization
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a fresh DAO cold-reads exactly what a prior DAO persisted through Gson`() {
        val list = listOf(
            entity(alarmId = "with-value", cause = AlarmCause.ALARM_NOTICE_LGS_FINISHED_HIGH_BG, value = 5, acknowledged = true, occurrenceCount = 3),
            entity(alarmId = "plain", cause = AlarmCause.ALARM_WARNING_PATCH_ERROR)
        )
        sut.setAlarms(list).blockingAwait()

        val fresh = CarelevoAlarmInfoDaoImpl(prefManager)
        val read = fresh.getAlarmsOnce().blockingGet()

        assertThat(read.isPresent).isTrue()
        // data-class equality covers alarmType, cause enum, value, occurrenceCount, acknowledged, timestamps.
        assertThat(read.get()).containsExactlyElementsIn(list)
    }

    @Test
    fun `verifies the fake never touches the StringRes overloads`() {
        // Sanity guard on the fake wiring: the DAO only uses the String-keyed SP overloads.
        seedStore(entity())
        sut.getAlarms().blockingFirst()
        sut.setAlarms(listOf(entity())).blockingAwait()
        sut.clearAlarms().blockingAwait()

        verify(prefManager, never()).getString(any<Int>(), any<String>())
        verify(prefManager, never()).putString(any<Int>(), any<String>())
        verify(prefManager, never()).remove(any<Int>())
    }
}

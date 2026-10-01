package app.aaps.pump.insight.descriptors

import app.aaps.pump.insight.app_layer.history.history_events.HistoryEvent
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Modifier
import org.junit.jupiter.api.Test

/**
 * Covers [HistoryEvents.Companion]: the table that turns a history event id from the pump into the
 * event object that parses it.
 *
 * Every record the pump reports comes through here, and the treatments AAPS records are built from
 * what the resulting object parses. An id pointing at the wrong class does not fail - the record is
 * simply read as a different kind of event, so a bolus could be stored as something else, or lost.
 * The ids are read back by reflection rather than typed out again, so these tests check the table
 * holds together instead of restating it.
 */
class HistoryEventsTest {

    /** Every `const val` event id declared on [HistoryEvents], as (name, id). */
    private val eventIds: List<Pair<String, Int>> =
        HistoryEvents::class.java.declaredFields
            .filter {
                Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers) &&
                    it.type == Int::class.javaPrimitiveType && !it.name.contains('$')
            }
            .map { field ->
                field.isAccessible = true
                field.name to field.getInt(null)
            }

    @Test
    fun theTestFindsTheEventIds() {
        // Guards the reflection: an empty list would make every other test here pass vacuously.
        assertThat(eventIds.size).isAtLeast(15)
    }

    /**
     * An id with no branch of its own falls through to the plain [HistoryEvent], which parses only
     * the header. The record would then be read as "something happened" with none of its detail.
     */
    @Test
    fun everyDeclaredIdBuildsAnEventOfItsOwnKind() {
        val fallenThrough = eventIds.filter { (_, id) ->
            HistoryEvents.fromId(id)::class == HistoryEvent::class
        }
        assertThat(fallenThrough.map { it.first }).isEmpty()
    }

    /** Two ids sharing an event class would mean one kind of record is read as another. */
    @Test
    fun noTwoIdsBuildTheSameKindOfEvent() {
        val byType = eventIds.groupBy { (_, id) -> HistoryEvents.fromId(id)::class }
        val shared = byType.filterValues { it.size > 1 }.map { (type, ids) ->
            "${type.simpleName} <- ${ids.map { it.first }}"
        }
        assertThat(shared).isEmpty()
    }

    /** Every event the table builds must really be a history event the parser can drive. */
    @Test
    fun everyBuiltEventIsAHistoryEvent() {
        val wrong = eventIds.filterNot { (_, id) -> HistoryEvents.fromId(id) is HistoryEvent }
        assertThat(wrong.map { it.first }).isEmpty()
    }

    /**
     * A firmware the driver does not know about must not crash the history read. An unknown id is
     * answered with the plain header-only event instead of null.
     */
    @Test
    fun anUnknownIdFallsBackToThePlainEvent() {
        assertThat(HistoryEvents.fromId(-1)::class).isEqualTo(HistoryEvent::class)
        assertThat(HistoryEvents.fromId(0)::class).isEqualTo(HistoryEvent::class)
        assertThat(HistoryEvents.fromId(Int.MAX_VALUE)::class).isEqualTo(HistoryEvent::class)
    }

    @Test
    fun theEventIdsAreAllDifferent() {
        val ids = eventIds.map { it.second }
        assertThat(ids.toSet()).hasSize(ids.size)
    }

    /** Asking twice must not hand back the same object, or two records would share parsed state. */
    @Test
    fun eachCallBuildsAFreshEvent() {
        val shared = eventIds.filter { (_, id) -> HistoryEvents.fromId(id) === HistoryEvents.fromId(id) }
        assertThat(shared.map { it.first }).isEmpty()
    }
}

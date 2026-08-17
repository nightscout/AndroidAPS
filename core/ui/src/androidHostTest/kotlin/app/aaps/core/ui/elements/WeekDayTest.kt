package app.aaps.core.ui.elements

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pins the weekday numbering, because getting it wrong is silent and wrong by exactly one day.
 *
 * `toCalendarInt()` / `getSelectedDays()` hand these numbers to `:plugins:automation` and
 * `:plugins:aps`, and automation triggers persist them - so the values are a stored contract, not an
 * implementation detail. They used to come from `java.util.Calendar`; they are now written out so
 * the class does not need the JVM. This test is what makes that swap safe: it compares the literals
 * against the real `Calendar` constants.
 */
class WeekDayTest {

    @Test
    fun `calendar ints match java util Calendar exactly`() {
        assertThat(WeekDay.DayOfWeek.MONDAY.toCalendarInt()).isEqualTo(Calendar.MONDAY)
        assertThat(WeekDay.DayOfWeek.TUESDAY.toCalendarInt()).isEqualTo(Calendar.TUESDAY)
        assertThat(WeekDay.DayOfWeek.WEDNESDAY.toCalendarInt()).isEqualTo(Calendar.WEDNESDAY)
        assertThat(WeekDay.DayOfWeek.THURSDAY.toCalendarInt()).isEqualTo(Calendar.THURSDAY)
        assertThat(WeekDay.DayOfWeek.FRIDAY.toCalendarInt()).isEqualTo(Calendar.FRIDAY)
        assertThat(WeekDay.DayOfWeek.SATURDAY.toCalendarInt()).isEqualTo(Calendar.SATURDAY)
        assertThat(WeekDay.DayOfWeek.SUNDAY.toCalendarInt()).isEqualTo(Calendar.SUNDAY)
    }

    @Test
    fun `fromCalendarInt round trips`() {
        WeekDay.DayOfWeek.entries.forEach { day ->
            assertThat(WeekDay.DayOfWeek.fromCalendarInt(day.toCalendarInt())).isEqualTo(day)
        }
    }

    @Test
    fun `a timestamp resolves to the same weekday as Calendar does`() {
        // Compared against Calendar in the default zone rather than asserted as a literal, so the
        // test says what it means - "the new implementation agrees with the old one" - and keeps
        // saying it wherever it runs.
        val day = 24 * 60 * 60 * 1000L
        var t = 1_767_225_600_000L // 2026-01-01T00:00:00Z
        repeat(14) {
            val expected = Calendar.getInstance(TimeZone.getDefault()).also { c -> c.timeInMillis = t }
                .get(Calendar.DAY_OF_WEEK)

            val weekDay = WeekDay()
            WeekDay.DayOfWeek.entries.forEach { weekDay[it] = false }
            weekDay[WeekDay.DayOfWeek.fromCalendarInt(expected)] = true

            assertThat(weekDay.isSet(t)).isTrue()
            t += day
        }
    }

    @Test
    fun `only the matching day is set`() {
        val monday = 1_767_398_400_000L // 2026-01-03T00:00:00Z, a Saturday in UTC
        val weekDay = WeekDay()
        val actual = WeekDay.DayOfWeek.entries.filter { day ->
            weekDay.setAll(false)
            weekDay[day] = true
            weekDay.isSet(monday)
        }
        assertThat(actual).hasSize(1)
    }
}

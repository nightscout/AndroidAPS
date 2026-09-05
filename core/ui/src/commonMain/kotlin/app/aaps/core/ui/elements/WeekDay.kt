package app.aaps.core.ui.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

open class WeekDay {

    enum class DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

        fun toCalendarInt(): Int {
            return calendarInts[ordinal]
        }

        val shortName: TextRef
            get() = shortNames[ordinal]

        companion object {

            // The numbers java.util.Calendar uses: SUNDAY is 1 and SATURDAY is 7, so Monday is 2.
            // They are written out rather than taken from Calendar because these values are a
            // persisted contract - automation triggers store them and `getSelectedDays()` hands
            // them to other modules - so they must not change, and they must not need the JVM.
            private val calendarInts = intArrayOf(2, 3, 4, 5, 6, 7, 1)
            private val shortNames = arrayOf(
                CoreUiStrings.weekday_monday_short,
                CoreUiStrings.weekday_tuesday_short,
                CoreUiStrings.weekday_wednesday_short,
                CoreUiStrings.weekday_thursday_short,
                CoreUiStrings.weekday_friday_short,
                CoreUiStrings.weekday_saturday_short,
                CoreUiStrings.weekday_sunday_short
            )

            fun fromCalendarInt(day: Int): DayOfWeek {
                for (i in calendarInts.indices) {
                    if (calendarInts[i] == day) return entries[i]
                }
                throw IllegalStateException("Invalid day")
            }

            /**
             * The day [now] falls on, in the phone's own time zone.
             *
             * The entries are declared Monday first, which is the same order ISO-8601 numbers them,
             * so the day number indexes them directly. That is the whole mapping - it is here rather
             * than at the call site so it sits beside [calendarInts], the other place where a day
             * number is turned into one of these.
             */
            fun of(now: Instant): DayOfWeek =
                entries[now.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.isoDayNumber - 1]
        }
    }

    val weekdays = BooleanArray(DayOfWeek.entries.size)

    init {
        for (day in DayOfWeek.entries) set(day, false)
    }

    fun setAll(value: Boolean) {
        for (day in DayOfWeek.entries) set(day, value)
    }

    operator fun set(day: DayOfWeek, value: Boolean): WeekDay {
        weekdays[day.ordinal] = value
        return this
    }

    fun isSet(day: DayOfWeek): Boolean = weekdays[day.ordinal]

    /**
     * Which weekday a moment falls on, in the device's own time zone - an automation set for Monday
     * has to mean the user's Monday, not UTC's.
     *
     * `kotlinx.datetime.DayOfWeek` runs MONDAY..SUNDAY, the same order as [DayOfWeek] here, so the
     * ordinal carries across directly and no ISO-number conversion is involved.
     */
    fun isSet(timestamp: Long): Boolean {
        val dayOfWeek = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .dayOfWeek
        return isSet(DayOfWeek.entries[dayOfWeek.ordinal])
    }

    fun getSelectedDays(): List<Int> {
        val selectedDays: MutableList<Int> = ArrayList()
        for (i in weekdays.indices) {
            val day = DayOfWeek.entries[i]
            val selected = weekdays[i]
            if (selected) selectedDays.add(day.toCalendarInt())
        }
        return selectedDays
    }
}

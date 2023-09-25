package app.aaps.core.ui.elements

import android.widget.LinearLayout
import androidx.annotation.StringRes
import app.aaps.core.ui.R
import java.util.Calendar
import java.util.Date

open class WeekDay {

    enum class DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

        fun toCalendarInt(): Int {
            return calendarInts[ordinal]
        }

        @get:StringRes val shortName: Int
            get() = shortNames[ordinal]

        companion object {

            private val calendarInts = intArrayOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
            )
            private val shortNames = intArrayOf(
                R.string.weekday_monday_short,
                R.string.weekday_tuesday_short,
                R.string.weekday_wednesday_short,
                R.string.weekday_thursday_short,
                R.string.weekday_friday_short,
                R.string.weekday_saturday_short,
                R.string.weekday_sunday_short
            )

            fun fromCalendarInt(day: Int): DayOfWeek {
                for (i in calendarInts.indices) {
                    if (calendarInts[i] == day) return entries[i]
                }
                throw IllegalStateException("Invalid day")
            }
        }
    }

    val weekdays = BooleanArray(DayOfWeek.entries.size)
    var view: WeekdayPicker? = null
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

    fun isSet(timestamp: Long): Boolean {
        val scheduledDayOfWeek = Calendar.getInstance().also { it.time = Date(timestamp) }
        return isSet(DayOfWeek.fromCalendarInt(scheduledDayOfWeek[Calendar.DAY_OF_WEEK]))
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

    fun addToLayout(root: LinearLayout) {
        view = WeekdayPicker(root.context).apply {
            setSelectedDays(getSelectedDays())
            setOnWeekdaysChangeListener { i: Int, selected: Boolean -> set(DayOfWeek.fromCalendarInt(i), selected) }
        }
        root.addView(
            view
        )
    }
}

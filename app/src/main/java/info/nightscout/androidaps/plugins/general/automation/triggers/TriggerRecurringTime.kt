package info.nightscout.androidaps.plugins.general.automation.triggers

import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.dpro.widgets.WeekdaysPicker
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import java.util.*

class TriggerRecurringTime(mainApp: MainApp) : Trigger(mainApp) {
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
                    if (calendarInts[i] == day) return values()[i]
                }
                throw IllegalStateException("Invalid day")
            }
        }
    }

    private val weekdays = BooleanArray(DayOfWeek.values().size)
    private var hour = 0
    private var minute = 0
    private var validTo: Long = 0

    constructor(mainApp: MainApp, triggerRecurringTime: TriggerRecurringTime) : this(mainApp) {
        this.hour = triggerRecurringTime.hour
        this.minute = triggerRecurringTime.minute
        this.validTo = triggerRecurringTime.validTo
        if (weekdays.size >= 0)
            System.arraycopy(triggerRecurringTime.weekdays, 0, weekdays, 0, triggerRecurringTime.weekdays.size)
    }

    init {
        for (day in DayOfWeek.values()) set(day, false)
    }

    operator fun set(day: DayOfWeek, value: Boolean): TriggerRecurringTime {
        weekdays[day.ordinal] = value
        return this
    }

    private fun isSet(day: DayOfWeek): Boolean = weekdays[day.ordinal]

    override fun shouldRun(): Boolean {
        if (validTo != 0L && DateUtil.now() > validTo) return false
        val c = Calendar.getInstance()
        val scheduledDayOfWeek = c[Calendar.DAY_OF_WEEK]
        val scheduledCal: Calendar = DateUtil.gregorianCalendar()
        scheduledCal[Calendar.HOUR_OF_DAY] = hour
        scheduledCal[Calendar.MINUTE] = minute
        scheduledCal[Calendar.SECOND] = 0
        val scheduled = scheduledCal.timeInMillis
        if (isSet(Objects.requireNonNull(DayOfWeek.fromCalendarInt(scheduledDayOfWeek)))) {
            if (DateUtil.now() >= scheduled && DateUtil.now() - scheduled < T.mins(5).msecs()) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                return true
            }
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("hour", hour)
            .put("minute", minute)
            .put("validTo", validTo)
        for (i in weekdays.indices) {
            data.put(DayOfWeek.values()[i].name, weekdays[i])
        }
        return JSONObject()
            .put("type", TriggerRecurringTime::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        for (i in weekdays.indices)
            weekdays[i] = JsonHelper.safeGetBoolean(o, DayOfWeek.values()[i].name)
        hour = JsonHelper.safeGetInt(o, "hour")
        minute = JsonHelper.safeGetInt(o, "minute")
        validTo = JsonHelper.safeGetLong(o, "validTo")
        return this
    }

    override fun friendlyName(): Int = R.string.recurringTime

    override fun friendlyDescription(): String {
        val sb = StringBuilder()
        sb.append(resourceHelper.gs(R.string.every))
        sb.append(" ")
        var counter = 0
        for (i in getSelectedDays()) {
            if (counter++ > 0) sb.append(",")
            sb.append(resourceHelper.gs(Objects.requireNonNull(DayOfWeek.fromCalendarInt(i)).shortName))
        }
        sb.append(" ")
        val scheduledCal: Calendar = DateUtil.gregorianCalendar()
        scheduledCal[Calendar.HOUR_OF_DAY] = hour
        scheduledCal[Calendar.MINUTE] = minute
        scheduledCal[Calendar.SECOND] = 0
        val scheduled = scheduledCal.timeInMillis
        sb.append(DateUtil.timeString(scheduled))
        return if (counter == 0) resourceHelper.gs(R.string.never) else sb.toString()
    }

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerRecurringTime(mainApp, this)

    private fun getSelectedDays(): List<Int> {
        val selectedDays: MutableList<Int> = ArrayList()
        for (i in weekdays.indices) {
            val day = DayOfWeek.values()[i]
            val selected = weekdays[i]
            if (selected) selectedDays.add(day.toCalendarInt())
        }
        return selectedDays
    }

    override fun generateDialog(root: LinearLayout) {
        val label = TextView(root.context)
        // TODO: Replace external tool WeekdaysPicker with a self-made GUI element
        val weekdaysPicker = WeekdaysPicker(root.context)
        weekdaysPicker.setEditable(true)
        weekdaysPicker.selectedDays = getSelectedDays()
        weekdaysPicker.setOnWeekdaysChangeListener { _: View?, i: Int, list: List<Int?> -> set(DayOfWeek.fromCalendarInt(i), list.contains(i)) }
        weekdaysPicker.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        weekdaysPicker.sundayFirstDay = Calendar.getInstance().firstDayOfWeek == Calendar.SUNDAY
        weekdaysPicker.redrawDays()
        root.addView(weekdaysPicker)

        val timeButton = TextView(root.context)
        val runAt = GregorianCalendar()
        runAt[Calendar.HOUR_OF_DAY] = hour
        runAt[Calendar.MINUTE] = minute
        timeButton.text = DateUtil.timeString(runAt.timeInMillis)

        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, h, m ->
            val cal = Calendar.getInstance()
            hour = h
            minute = m
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            timeButton.text = DateUtil.timeString(cal.timeInMillis)
        }

        timeButton.setOnClickListener {
            root.context?.let {
                TimePickerDialog(it, timeSetListener, hour, minute, DateFormat.is24HourFormat(mainApp))
                    .show()
            }
        }

        val px = resourceHelper.dpToPx(10)
        label.text = resourceHelper.gs(R.string.atspecifiedtime, "")
        label.setTypeface(label.typeface, Typeface.BOLD)
        label.setPadding(px, px, px, px)
        timeButton.setPadding(px, px, px, px)
        val l = LinearLayout(root.context)
        l.orientation = LinearLayout.HORIZONTAL
        l.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.addView(label)
        l.addView(timeButton)
        root.addView(l)
    }
}
package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputTime
import app.aaps.plugins.automation.elements.InputWeekDay
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Calendar
import java.util.Objects
import java.util.Optional

class TriggerRecurringTime(injector: HasAndroidInjector) : Trigger(injector) {

    val days = InputWeekDay()
    val time = InputTime(rh, dateUtil)

    constructor(injector: HasAndroidInjector, triggerRecurringTime: TriggerRecurringTime) : this(injector) {
        time.value = triggerRecurringTime.time.value
        if (days.weekdays.size >= 0)
            System.arraycopy(triggerRecurringTime.days.weekdays, 0, days.weekdays, 0, triggerRecurringTime.days.weekdays.size)
    }

    fun time(minutes: Int): TriggerRecurringTime {
        time.value = minutes
        return this
    }

    override fun shouldRun(): Boolean {
        val currentMinSinceMidnight = getMinSinceMidnight(dateUtil.now())
        val scheduledDayOfWeek = Calendar.getInstance()[Calendar.DAY_OF_WEEK]
        if (days.isSet(Objects.requireNonNull(WeekDay.DayOfWeek.fromCalendarInt(scheduledDayOfWeek)))) {
            if (currentMinSinceMidnight >= time.value && currentMinSinceMidnight - time.value < 5) {
                aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
                return true
            }
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject {
        val data = JSONObject()
            .put("time", time.value)
        for (i in days.weekdays.indices) {
            data.put(WeekDay.DayOfWeek.entries[i].name, days.weekdays[i])
        }
        return data
    }

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        for (i in days.weekdays.indices)
            days.weekdays[i] = JsonHelper.safeGetBoolean(o, WeekDay.DayOfWeek.entries[i].name)
        if (o.has("hour")) {
            // do conversion from 2.5.1 format
            val hour = JsonHelper.safeGetInt(o, "hour")
            val minute = JsonHelper.safeGetInt(o, "minute")
            time.value = 60 * hour + minute
        } else {
            time.value = JsonHelper.safeGetInt(o, "time")
        }
        return this
    }

    override fun friendlyName(): Int = R.string.recurringTime

    override fun friendlyDescription(): String {
        val sb = StringBuilder()
        sb.append(rh.gs(R.string.every))
        sb.append(" ")
        var counter = 0
        for (i in days.getSelectedDays()) {
            if (counter++ > 0) sb.append(",")
            sb.append(rh.gs(Objects.requireNonNull(WeekDay.DayOfWeek.fromCalendarInt(i)).shortName))
        }
        sb.append(" ")
        sb.append(dateUtil.timeString(toMills(time.value)))
        return if (counter == 0) rh.gs(R.string.never) else sb.toString()
    }

    override fun icon(): Optional<Int> = Optional.of(app.aaps.core.objects.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerRecurringTime(injector, this)

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcMidnightPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.recurringTime, this))
            .add(days)
            .add(time)
            .build(root)
    }
}
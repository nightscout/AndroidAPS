package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputTime
import info.nightscout.automation.elements.InputWeekDay
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.core.utils.MidnightUtils
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.rx.logging.LTag
import org.json.JSONObject
import java.util.Calendar
import java.util.Objects

class TriggerRecurringTime(injector: HasAndroidInjector) : Trigger(injector) {

    val days = InputWeekDay()
    val time = InputTime(rh, dateUtil)

    constructor(injector: HasAndroidInjector, triggerRecurringTime: TriggerRecurringTime) : this(injector) {
        this.time.value = triggerRecurringTime.time.value
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
        if (days.isSet(Objects.requireNonNull(InputWeekDay.DayOfWeek.fromCalendarInt(scheduledDayOfWeek)))) {
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
            data.put(InputWeekDay.DayOfWeek.values()[i].name, days.weekdays[i])
        }
        return data
    }

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        for (i in days.weekdays.indices)
            days.weekdays[i] = JsonHelper.safeGetBoolean(o, InputWeekDay.DayOfWeek.values()[i].name)
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
            sb.append(rh.gs(Objects.requireNonNull(InputWeekDay.DayOfWeek.fromCalendarInt(i)).shortName))
        }
        sb.append(" ")
        sb.append(dateUtil.timeString(toMills(time.value)))
        return if (counter == 0) rh.gs(R.string.never) else sb.toString()
    }

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.core.main.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerRecurringTime(injector, this)

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.recurringTime, this))
            .add(days)
            .add(time)
            .build(root)
    }
}
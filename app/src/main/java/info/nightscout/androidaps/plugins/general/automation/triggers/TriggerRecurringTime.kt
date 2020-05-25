package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.InputTime
import info.nightscout.androidaps.plugins.general.automation.elements.InputWeekDay
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.MidnightTime
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class TriggerRecurringTime(injector: HasAndroidInjector) : Trigger(injector) {
    @Inject lateinit var dateUtil: DateUtil

    val days = InputWeekDay(injector)
    val time = InputTime(injector)

    constructor(injector: HasAndroidInjector, triggerRecurringTime: TriggerRecurringTime) : this(injector) {
        this.time.value = triggerRecurringTime.time.value
        if (days.weekdays.size >= 0)
            System.arraycopy(triggerRecurringTime.days.weekdays, 0, days.weekdays, 0, triggerRecurringTime.days.weekdays.size)
    }

    fun time(minutes: Int): TriggerRecurringTime {
        time.value = minutes
        return this
    }

    override fun shouldRun() : Boolean {
        val currentMinSinceMidnight = getMinSinceMidnight(dateUtil._now())
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

    override fun toJSON(): String {
        val data = JSONObject()
            .put("time", time.value)
        for (i in days.weekdays.indices) {
            data.put(InputWeekDay.DayOfWeek.values()[i].name, days.weekdays[i])
        }
        return JSONObject()
            .put("type", TriggerRecurringTime::class.java.name)
            .put("data", data)
            .toString()
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
        sb.append(resourceHelper.gs(R.string.every))
        sb.append(" ")
        var counter = 0
        for (i in days.getSelectedDays()) {
            if (counter++ > 0) sb.append(",")
            sb.append(resourceHelper.gs(Objects.requireNonNull(InputWeekDay.DayOfWeek.fromCalendarInt(i)).shortName))
        }
        sb.append(" ")
        sb.append(dateUtil.timeString(toMills(time.value)))
        return if (counter == 0) resourceHelper.gs(R.string.never) else sb.toString()
    }

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerRecurringTime(injector, this)

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = Profile.secondsFromMidnight(time) / 60

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(injector, R.string.recurringTime, this))
            .add(days)
            .add(time)
            .build(root)
    }
}
package app.aaps.plugins.automation.triggers

import android.widget.LinearLayout
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputWeekDay
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Calendar
import java.util.Objects
import java.util.Optional

class TriggerDay(injector: HasAndroidInjector) : Trigger(injector) {

    val days = InputWeekDay()

    constructor(injector: HasAndroidInjector, triggerDay: TriggerDay) : this(injector) {
        if (days.weekdays.size >= 0)
            System.arraycopy(triggerDay.days.weekdays, 0, days.weekdays, 0, triggerDay.days.weekdays.size)
    }

    override fun shouldRun(): Boolean {
        val scheduledDayOfWeek = Calendar.getInstance()[Calendar.DAY_OF_WEEK]
        if (days.isSet(Objects.requireNonNull(WeekDay.DayOfWeek.fromCalendarInt(scheduledDayOfWeek)))) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JSONObject {
        val data = JSONObject()
        for (i in days.weekdays.indices) {
            data.put(WeekDay.DayOfWeek.entries[i].name, days.weekdays[i])
        }
        return data
    }

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        for (i in days.weekdays.indices) {
            days.weekdays[i] = JsonHelper.safeGetBoolean(o, WeekDay.DayOfWeek.entries[i].name)
        }

        return this
    }

    override fun friendlyName(): Int = R.string.day

    override fun friendlyDescription(): String {
        val sb = StringBuilder()
        sb.append(rh.gs(R.string.every))
        sb.append(" ")
        var counter = 0
        for (i in days.getSelectedDays()) {
            if (counter++ > 0) sb.append(",")
            sb.append(rh.gs(Objects.requireNonNull(WeekDay.DayOfWeek.fromCalendarInt(i)).shortName))
        }
        return if (counter == 0) rh.gs(R.string.never) else sb.toString()
    }

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_calendar_days)

    override fun duplicate(): Trigger = TriggerDay(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.day, this))
            .add(days)
            .build(root)
    }
}
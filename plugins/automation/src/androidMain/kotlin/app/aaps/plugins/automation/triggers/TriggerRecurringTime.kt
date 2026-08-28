package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientInt
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputTime
import app.aaps.plugins.automation.elements.InputWeekDay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import java.util.Objects

class TriggerRecurringTime(deps: TriggerDeps) : Trigger(deps) {

    val days = InputWeekDay()
    val time = InputTime(rh, dateUtil)

    constructor(deps: TriggerDeps, triggerRecurringTime: TriggerRecurringTime) : this(deps) {
        time.value = triggerRecurringTime.time.value
        if (days.weekdays.size >= 0)
            System.arraycopy(triggerRecurringTime.days.weekdays, 0, days.weekdays, 0, triggerRecurringTime.days.weekdays.size)
    }

    fun time(minutes: Int): TriggerRecurringTime {
        time.value = minutes
        return this
    }

    override suspend fun shouldRun(): Boolean {
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

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("time", time.value)
            for (i in days.weekdays.indices) {
                put(WeekDay.DayOfWeek.entries[i].name, days.weekdays[i])
            }
        }

    override fun fromJSON(data: String): Trigger {
        val o = jsonOf(data)
        for (i in days.weekdays.indices)
            days.weekdays[i] = o.lenientBoolean(WeekDay.DayOfWeek.entries[i].name, false)
        if (o.containsKey("hour")) {
            // do conversion from 2.5.1 format
            val hour = o.lenientInt("hour")
            val minute = o.lenientInt("minute")
            time.value = 60 * hour + minute
        } else {
            time.value = o.lenientInt("time")
        }
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.recurringTime

    override fun friendlyDescription(): String {
        val sb = StringBuilder()
        sb.append(rh.gs(AutomationStrings.every))
        sb.append(" ")
        var counter = 0
        for (i in days.getSelectedDays()) {
            if (counter++ > 0) sb.append(",")
            sb.append(rh.gs(Objects.requireNonNull(WeekDay.DayOfWeek.fromCalendarInt(i)).shortName))
        }
        sb.append(" ")
        sb.append(dateUtil.timeString(toMills(time.value)))
        return if (counter == 0) rh.gs(AutomationStrings.never) else sb.toString()
    }

    override fun composeIcon() = Icons.Filled.Repeat
    override fun elementType() = ElementType.AUTOMATION

    override fun duplicate(): Trigger = TriggerRecurringTime(deps, this)

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcMidnightPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60

}
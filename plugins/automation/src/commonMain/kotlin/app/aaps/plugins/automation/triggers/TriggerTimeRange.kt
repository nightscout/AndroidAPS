package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.utils.lenientInt
import app.aaps.plugins.automation.elements.InputTimeRange
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Trigger for time range ( from 10:00AM till 13:00PM )
class TriggerTimeRange(deps: TriggerDeps) : Trigger(deps) {

    // in minutes since midnight 60 means 1AM
    var range = InputTimeRange(rh, dateUtil)

    constructor(deps: TriggerDeps, start: Int, end: Int) : this(deps) {
        range.start = start
        range.end = end
    }

    @Suppress("unused")
    constructor(deps: TriggerDeps, triggerTimeRange: TriggerTimeRange) : this(deps) {
        range.start = triggerTimeRange.range.start
        range.end = triggerTimeRange.range.end
    }

    fun period(start: Int, end: Int): TriggerTimeRange {
        this.range.start = start
        this.range.end = end
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val currentMinSinceMidnight = getMinSinceMidnight(dateUtil.now())
        var doRun = false
        if (range.start < range.end && range.start < currentMinSinceMidnight && currentMinSinceMidnight < range.end) doRun = true
        else if (range.start > range.end && (range.start < currentMinSinceMidnight || currentMinSinceMidnight < range.end)) doRun = true
        if (doRun) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("start", range.start)
            put("end", range.end)
        }

    override fun fromJSON(data: String): TriggerTimeRange {
        val o = jsonOf(data)
        range.start = o.lenientInt("start")
        range.end = o.lenientInt("end")
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.time_range

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.timerange_value, dateUtil.timeString(toMills(range.start)), dateUtil.timeString(toMills(range.end)))

    override fun composeIcon() = Icons.Filled.Timer
    override fun elementType() = ElementType.AUTOMATION

    override fun duplicate(): Trigger = TriggerTimeRange(deps, range.start, range.end)

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcMidnightPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60

}
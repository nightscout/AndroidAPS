package info.nightscout.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputTimeRange
import info.nightscout.automation.elements.LayoutBuilder
import info.nightscout.automation.elements.StaticLabel
import info.nightscout.core.utils.MidnightUtils
import info.nightscout.interfaces.utils.JsonHelper.safeGetInt
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.rx.logging.LTag
import org.json.JSONObject

// Trigger for time range ( from 10:00AM till 13:00PM )
class TriggerTimeRange(injector: HasAndroidInjector) : Trigger(injector) {

    // in minutes since midnight 60 means 1AM
    var range = InputTimeRange(rh, dateUtil)

    constructor(injector: HasAndroidInjector, start: Int, end: Int) : this(injector) {
        range.start = start
        range.end = end
    }

    @Suppress("unused")
    constructor(injector: HasAndroidInjector, triggerTimeRange: TriggerTimeRange) : this(injector) {
        range.start = triggerTimeRange.range.start
        range.end = triggerTimeRange.range.end
    }

    fun period(start: Int, end: Int): TriggerTimeRange {
        this.range.start = start
        this.range.end = end
        return this
    }

    override fun shouldRun(): Boolean {
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

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("start", range.start)
            .put("end", range.end)

    override fun fromJSON(data: String): TriggerTimeRange {
        val o = JSONObject(data)
        range.start = safeGetInt(o, "start")
        range.end = safeGetInt(o, "end")
        return this
    }

    override fun friendlyName(): Int = R.string.time_range

    override fun friendlyDescription(): String =
        rh.gs(R.string.timerange_value, dateUtil.timeString(toMills(range.start)), dateUtil.timeString(toMills(range.end)))

    override fun icon(): Optional<Int> = Optional.of(info.nightscout.core.main.R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerTimeRange(injector, range.start, range.end)

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.time_range, this))
            .add(range)
            .build(root)
    }
}
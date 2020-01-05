package info.nightscout.androidaps.plugins.general.automation.triggers

import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper.safeGetInt
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import java.util.*

// Trigger for time range ( from 10:00AM till 13:00PM )
class TriggerTimeRange(mainApp: MainApp) : Trigger(mainApp) {

    // in minutes since midnight 60 means 1AM
    var start: Int = getMinSinceMidnight(DateUtil.now())
    var end: Int = getMinSinceMidnight(DateUtil.now())

    constructor(mainApp: MainApp, start : Int, end :Int) : this(mainApp) {
        this.start = start
        this.end = end
    }

    constructor(mainApp: MainApp, triggerTimeRange: TriggerTimeRange) : this(mainApp) {
        this.start = triggerTimeRange.start
        this.end = triggerTimeRange.end
    }

    override fun shouldRun(): Boolean {
        val currentMinSinceMidnight = getMinSinceMidnight(DateUtil.now())
        var doRun = false
        if (start < end && start < currentMinSinceMidnight && currentMinSinceMidnight < end) doRun = true
        else if (start > end && (start < currentMinSinceMidnight || currentMinSinceMidnight < end)) doRun = true
        if (doRun) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("start", getMinSinceMidnight(start.toLong()))
            .put("end", getMinSinceMidnight(end.toLong()))
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): TriggerTimeRange {
        val o = JSONObject(data)
        start = safeGetInt(o, "start")
        end = safeGetInt(o, "end")
        return this
    }

    override fun friendlyName(): Int = R.string.time_range

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.timerange_value, DateUtil.timeString(toMills(start)), DateUtil.timeString(toMills(end)))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger  = TriggerTimeRange(mainApp, start, end)

    private fun toMills(minutesSinceMidnight: Int): Long = T.secs(minutesSinceMidnight.toLong()).msecs()

    private fun getMinSinceMidnight(time: Long): Int = Profile.secondsFromMidnight(time) / 60

    override fun generateDialog(root: LinearLayout) {
        val label = TextView(root.context)
        val startButton = TextView(root.context)
        val endButton = TextView(root.context)
        startButton.text = DateUtil.timeString(toMills(start))
        @Suppress("SetTextI18n")
        endButton.text = resourceHelper.gs(R.string.and) + " " + DateUtil.timeString(toMills(end))

        val startTimeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = toMills(start)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            start = getMinSinceMidnight(cal.timeInMillis)
            startButton.text = DateUtil.timeString(toMills(start))
        }

        startButton.setOnClickListener {
            root.context?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = toMills(start)
                TimePickerDialog(it, startTimeSetListener,
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(mainApp)
                ).show()
            }
        }

        val endTimeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = toMills(end)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0) // randomize seconds to prevent creating record of the same time, if user choose time manually
            end = getMinSinceMidnight(cal.timeInMillis)
            endButton.text = DateUtil.timeString(toMills(end))
        }

        endButton.setOnClickListener {
            root.context?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = toMills(end)
                TimePickerDialog(it, endTimeSetListener,
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(mainApp)
                ).show()
            }
        }

        val px = resourceHelper.dpToPx(10)
        label.text = resourceHelper.gs(R.string.between)
        label.setTypeface(label.typeface, Typeface.BOLD)
        startButton.setPadding(px, px, px, px)
        endButton.setPadding(px, px, px, px)
        val l = LinearLayout(root.context)
        l.orientation = LinearLayout.HORIZONTAL
        l.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.addView(label)
        l.addView(startButton)
        l.addView(endButton)
        root.addView(l)
    }
}
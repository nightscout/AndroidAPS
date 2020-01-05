package info.nightscout.androidaps.plugins.general.automation.triggers

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper.safeGetLong
import info.nightscout.androidaps.utils.T
import org.json.JSONObject
import java.util.*

class TriggerTime(mainApp: MainApp) : Trigger(mainApp) {
    var runAt = DateUtil.now()

    constructor(mainApp: MainApp, runAt: Long) : this(mainApp) {
        this.runAt = runAt
    }

    constructor(mainApp: MainApp, triggerTime : TriggerTime) : this(mainApp) {
        this.runAt = triggerTime.runAt
    }

    override fun shouldRun(): Boolean {
        val now = DateUtil.now()
        if (now >= runAt && now - runAt < T.mins(5).msecs()) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("runAt", runAt)
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val o = JSONObject(data)
        runAt = safeGetLong(o, "runAt")
        return this
    }

    override fun friendlyName(): Int = R.string.time

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.atspecifiedtime, DateUtil.dateAndTimeString(runAt))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_access_alarm_24dp)

    override fun duplicate(): Trigger = TriggerTime(mainApp, runAt)

    override fun generateDialog(root: LinearLayout) {
        val label = TextView(root.context)
        val dateButton = TextView(root.context)
        val timeButton = TextView(root.context)
        dateButton.text = DateUtil.dateString(runAt)
        timeButton.text = DateUtil.timeString(runAt)

        // create an OnDateSetListener
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = runAt
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, monthOfYear)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            runAt = cal.timeInMillis
            dateButton.text = DateUtil.dateString(runAt)
        }

        dateButton.setOnClickListener {
            root.context?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = runAt
                DatePickerDialog(it, dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        // create an OnTimeSetListener
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = runAt
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0) // randomize seconds to prevent creating record of the same time, if user choose time manually
            runAt = cal.timeInMillis
            timeButton.text = DateUtil.timeString(runAt)
        }

        timeButton.setOnClickListener {
            root.context?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = runAt
                TimePickerDialog(it, timeSetListener,
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(mainApp)
                ).show()
            }
        }

        val px = resourceHelper.dpToPx(10)
        label.text = resourceHelper.gs(R.string.atspecifiedtime, "")
        label.setTypeface(label.typeface, Typeface.BOLD)
        label.setPadding(px, px, px, px)
        dateButton.setPadding(px, px, px, px)
        timeButton.setPadding(px, px, px, px)
        val l = LinearLayout(root.context)
        l.orientation = LinearLayout.HORIZONTAL
        l.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.addView(label)
        l.addView(dateButton)
        l.addView(timeButton)
        root.addView(l)
    }
}
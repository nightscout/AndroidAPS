package info.nightscout.androidaps.plugins.general.automation.elements

import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.T
import java.util.*

class InputTimeRange(mainApp: MainApp) : Element(mainApp) {
    var start: Int = getMinSinceMidnight(DateUtil.now())
    var end: Int = getMinSinceMidnight(DateUtil.now())

    override fun addToLayout(root: LinearLayout) {
        val label = TextView(root.context)
        val startButton = TextView(root.context)
        val endButton = TextView(root.context)
        startButton.text = DateUtil.timeString(toMills(start))
        @Suppress("SetTextI18n")
        endButton.text = resourceHelper.gs(R.string.and) + "      " + DateUtil.timeString(toMills(end))

        val startTimeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            start = 60 * hour + minute
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
            end = 60 * hour + minute
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

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calc() + T.mins(minutesSinceMidnight.toLong()).msecs()

    private fun getMinSinceMidnight(time: Long): Int = Profile.secondsFromMidnight(time) / 60
}
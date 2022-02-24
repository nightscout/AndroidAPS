package info.nightscout.androidaps.plugins.general.automation.elements

import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*

class InputTimeRange(private val rh: ResourceHelper, private val dateUtil: DateUtil) : Element() {

    var start: Int = getMinSinceMidnight(dateUtil.now())
    var end: Int = getMinSinceMidnight(dateUtil.now())

    override fun addToLayout(root: LinearLayout) {
        val px = rh.dpToPx(10)

        root.addView(
            TextView(root.context).apply {
                text = rh.gs(R.string.between)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_HORIZONTAL
            })
        root.addView(
            LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER_HORIZONTAL
                addView(
                    TextView(root.context).apply {
                        text = dateUtil.timeString(toMills(start))
                        setPadding(px, px, px, px)
                        setOnClickListener {
                            root.context?.let {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = toMills(start)
                                TimePickerDialog(
                                    it, R.style.MaterialPickerTheme,
                                    { _, hour, minute ->
                                        start = 60 * hour + minute
                                        text = dateUtil.timeString(toMills(start))
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    DateFormat.is24HourFormat(it)
                                ).show()
                            }
                        }
                    })
                addView(TextView(root.context).apply {
                    @Suppress("SetTextI18n")
                    text = rh.gs(R.string.and) + "      " + dateUtil.timeString(toMills(end))
                    setPadding(px, px, px, px)
                    setOnClickListener {
                        root.context?.let {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = toMills(end)
                            TimePickerDialog(
                                it, R.style.MaterialPickerTheme,
                                { _, hour, minute ->
                                    end = 60 * hour + minute
                                    text = dateUtil.timeString(toMills(end))
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                DateFormat.is24HourFormat(it)
                            ).show()
                        }
                    }
                })
            })
    }

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = Profile.secondsFromMidnight(time) / 60
}
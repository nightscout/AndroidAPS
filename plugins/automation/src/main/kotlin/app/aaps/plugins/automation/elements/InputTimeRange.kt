package app.aaps.plugins.automation.elements

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.automation.R
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class InputTimeRange(private val rh: ResourceHelper, private val dateUtil: DateUtil) : Element {

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
                            getFragmentManager(root.context)?.let { fm ->
                                val cal = Calendar.getInstance().apply { timeInMillis = toMills(start) }
                                val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
                                val timePicker = MaterialTimePicker.Builder()
                                    .setTimeFormat(clockFormat)
                                    .setHour(cal[Calendar.HOUR_OF_DAY])
                                    .setMinute(cal[Calendar.MINUTE])
                                    .build()
                                timePicker.addOnPositiveButtonClickListener {
                                    start = 60 * timePicker.hour + timePicker.minute
                                    text = dateUtil.timeString(toMills(start))
                                }
                                timePicker.show(fm, "input_time_range_start_picker")
                            }
                        }
                    })
                addView(TextView(root.context).apply {
                    @Suppress("SetTextI18n")
                    text = rh.gs(app.aaps.core.ui.R.string.and) + "      " + dateUtil.timeString(toMills(end))
                    setPadding(px, px, px, px)
                    setOnClickListener {
                        getFragmentManager(root.context)?.let { fm ->
                            val cal = Calendar.getInstance().apply { timeInMillis = toMills(end) }
                            val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
                            val timePicker = MaterialTimePicker.Builder()
                                .setTimeFormat(clockFormat)
                                .setHour(cal[Calendar.HOUR_OF_DAY])
                                .setMinute(cal[Calendar.MINUTE])
                                .build()
                            timePicker.addOnPositiveButtonClickListener {
                                end = 60 * timePicker.hour + timePicker.minute
                                text = dateUtil.timeString(toMills(end))
                            }
                            timePicker.show(fm, "input_time_range_end_picker")
                        }
                    }
                })
            })
    }

    private fun toMills(minutesSinceMidnight: Int): Long = MidnightTime.calcMidnightPlusMinutes(minutesSinceMidnight)

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60

    private fun getFragmentManager(context: Context?): FragmentManager? {
        return when (context) {
            is AppCompatActivity -> context.supportFragmentManager
            is ContextThemeWrapper -> getFragmentManager(context.baseContext)
            else -> null
        }
    }
}

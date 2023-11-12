package app.aaps.plugins.automation.elements

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
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

class InputTime(private val rh: ResourceHelper, private val dateUtil: DateUtil) : Element {

    var value: Int = getMinSinceMidnight(dateUtil.now())

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(
                    TextView(root.context).apply {
                        text = rh.gs(R.string.atspecifiedtime, "")
                        setTypeface(typeface, Typeface.BOLD)
                    })
                addView(
                    TextView(root.context).apply {
                        text = dateUtil.timeString(toMills(value))
                        val px = rh.dpToPx(10)
                        setPadding(px, px, px, px)
                        setOnClickListener {
                            getFragmentManager(root.context)?.let { fm ->
                                val cal = Calendar.getInstance().apply { timeInMillis = toMills(value) }
                                val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
                                val timePicker = MaterialTimePicker.Builder()
                                    .setTimeFormat(clockFormat)
                                    .setHour(cal[Calendar.HOUR_OF_DAY])
                                    .setMinute(cal[Calendar.MINUTE])
                                    .build()
                                timePicker.addOnPositiveButtonClickListener {
                                    value = 60 * timePicker.hour + timePicker.minute
                                    text = dateUtil.timeString(toMills(value))
                                }
                                timePicker.show(fm, "input_time_picker")
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

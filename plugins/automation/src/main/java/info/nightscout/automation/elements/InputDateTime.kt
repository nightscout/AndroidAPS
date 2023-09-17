package info.nightscout.automation.elements

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import info.nightscout.automation.R
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import java.util.Calendar

class InputDateTime(private val rh: ResourceHelper, private val dateUtil: DateUtil, var value: Long = dateUtil.now()) : Element {

    override fun addToLayout(root: LinearLayout) {
        val px = rh.dpToPx(10)

        root.addView(
            LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(
                    TextView(root.context).apply {
                        text = rh.gs(R.string.atspecifiedtime, "")
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(px, px, px, px)
                    })
                addView(
                    TextView(root.context).apply {
                        text = dateUtil.dateString(value)
                        setPadding(px, px, px, px)
                        setOnClickListener {
                            getFragmentManager(root.context)?.let { fm ->
                                MaterialDatePicker.Builder.datePicker()
                                    .setTheme(info.nightscout.core.ui.R.style.DatePicker)
                                    .setSelection(dateUtil.timeStampToUtcDateMillis(value))
                                    .build()
                                    .apply {
                                        addOnPositiveButtonClickListener { selection ->
                                            value = dateUtil.mergeUtcDateToTimestamp(value, selection)
                                            text = dateUtil.dateString(value)
                                        }
                                    }
                                    .show(fm, "input_date_picker")
                            }
                        }
                    })
                addView(
                    TextView(root.context).apply {
                        text = dateUtil.timeString(value)
                        setPadding(px, px, px, px)
                        setOnClickListener {
                            getFragmentManager(root.context)?.let { fm ->
                                val cal = Calendar.getInstance().apply { timeInMillis = value }
                                val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
                                val timePicker = MaterialTimePicker.Builder()
                                    .setTheme(info.nightscout.core.ui.R.style.TimePicker)
                                    .setTimeFormat(clockFormat)
                                    .setHour(cal.get(Calendar.HOUR_OF_DAY))
                                    .setMinute(cal.get(Calendar.MINUTE))
                                    .build()
                                timePicker.addOnPositiveButtonClickListener {
                                    value = dateUtil.mergeHourMinuteToTimestamp(value, timePicker.hour, timePicker.minute)
                                    text = dateUtil.timeString(value)
                                }
                                timePicker.show(fm, "input_time_picker")
                            }
                        }
                    }
                )
            })
    }

    private fun getFragmentManager(context: Context?): FragmentManager? {
        return when (context) {
            is AppCompatActivity -> context.supportFragmentManager
            is ContextThemeWrapper -> getFragmentManager(context.baseContext)
            else -> null
        }
    }
}

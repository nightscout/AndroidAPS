package info.nightscout.androidaps.plugins.general.automation.elements

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*

class InputDateTime(private val rh: ResourceHelper, private val dateUtil: DateUtil, var value: Long = dateUtil.now()) : Element() {

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
                            root.context?.let {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = value
                                DatePickerDialog(
                                    it, R.style.MaterialPickerTheme,
                                    { _, year, monthOfYear, dayOfMonth ->
                                        value = Calendar.getInstance().apply {
                                            timeInMillis = value
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, monthOfYear)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }.timeInMillis
                                        text = dateUtil.dateString(value)
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        }
                    })
                addView(
                    TextView(root.context).apply {
                        text = dateUtil.timeString(value)
                        setPadding(px, px, px, px)
                        setOnClickListener {
                            root.context?.let {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = value
                                TimePickerDialog(
                                    it, R.style.MaterialPickerTheme,
                                    { _, hour, minute ->
                                        value = Calendar.getInstance().apply {
                                            timeInMillis = value
                                            set(Calendar.HOUR_OF_DAY, hour)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0) // randomize seconds to prevent creating record of the same time, if user choose time manually
                                        }.timeInMillis
                                        text = dateUtil.timeString(value)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    DateFormat.is24HourFormat(it)
                                ).show()
                            }
                        }
                    }
                )
            })
    }
}
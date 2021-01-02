package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import info.nightscout.androidaps.core.R
import java.text.DecimalFormat

class MinutesNumberPicker constructor(context: Context, attrs: AttributeSet? = null) : NumberPicker(context, attrs) {

    fun setParams(initValue: Double, minValue: Double, maxValue: Double, step: Double, allowZero: Boolean, okButton: Button? = null) {
        super.setParams(initValue, minValue, maxValue, step, null, allowZero, okButton)
    }

    override fun updateEditText() {
        if (value == 0.0 && !allowZero) editText.setText("")
        else {
            if (focused) editText.setText(DecimalFormat("0").format(value))
            else {
                val hours = (value / 60).toInt()
                val minutes = (value - hours * 60).toInt()
                val formatted =
                    if (hours != 0) String.format(context.getString(R.string.format_hour_minute), hours, minutes)
                    else DecimalFormat("0").format(value)
                editText.setText(formatted)
            }
        }
    }

}
package app.aaps.core.ui.elements

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import app.aaps.core.ui.R
import java.text.DecimalFormat

class MinutesNumberPicker(context: Context, attrs: AttributeSet? = null) : NumberPicker(context, attrs) {

    fun setParams(initValue: Double, minValue: Double, maxValue: Double, step: Double, allowZero: Boolean, okButton: Button? = null) {
        super.setParams(initValue, minValue, maxValue, step, null, allowZero, okButton, null)
    }

    override fun updateEditText() {
        if (currentValue == 0.0 && !allowZero) binding.editText.setText("")
        else {
            if (focused) binding.editText.setText(DecimalFormat("0").format(currentValue))
            else {
                val hours = (currentValue / 60).toInt()
                val minutes = (currentValue - hours * 60).toInt()
                val formatted =
                    if (hours != 0) String.format(context.getString(R.string.format_hour_minute), hours, minutes)
                    else DecimalFormat("0").format(currentValue)
                binding.editText.setText(formatted)
            }
        }
    }

}

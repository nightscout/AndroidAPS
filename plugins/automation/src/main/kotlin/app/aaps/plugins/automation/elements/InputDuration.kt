package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import app.aaps.core.ui.elements.MinutesNumberPicker
import app.aaps.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputDuration(
    var value: Int = 0,
    var unit: TimeUnit = TimeUnit.MINUTES,
) : Element {

    enum class TimeUnit {
        MINUTES, HOURS, DAYS
    }

    override fun addToLayout(root: LinearLayout) {
        val numberPicker: NumberPicker
        when (unit) {
            TimeUnit.MINUTES -> {
                numberPicker = MinutesNumberPicker(root.context, null)
                numberPicker.setParams(value.toDouble(), 5.0, 24 * 60.0, 10.0, DecimalFormat("0"), false, root.findViewById(app.aaps.core.ui.R.id.ok))
            }
            TimeUnit.DAYS    -> {
                numberPicker = MinutesNumberPicker(root.context, null)
                numberPicker.setParams(value.toDouble(), 1.0, 30.0, 1.0, DecimalFormat("0"), false, root.findViewById(app.aaps.core.ui.R.id.ok))
            }
            else             -> {
                numberPicker = NumberPicker(root.context, null)
                numberPicker.setParams(value.toDouble(), 1.0, 24.0, 1.0, DecimalFormat("0"), false, root.findViewById(app.aaps.core.ui.R.id.ok))
            }
        }
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value.toInt() }
        numberPicker.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(numberPicker)
    }

    fun duplicate(): InputDuration {
        val i = InputDuration()
        i.unit = unit
        i.value = value
        return i
    }

    fun getMinutes(): Int = if (unit == TimeUnit.MINUTES) value else value * 60

    fun setMinutes(value: Int): InputDuration {
        if (unit == TimeUnit.MINUTES)
            this.value = value
        else
            this.value = value / 60
        return this
    }

    override fun toString(): String = "InputDuration: $value $unit"
}
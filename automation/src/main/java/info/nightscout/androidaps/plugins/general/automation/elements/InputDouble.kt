package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputDouble() : Element() {
    var value = 0.0
    private var minValue = 0.0
    private var maxValue = 0.0
    private var step = 0.0
    private var decimalFormat: DecimalFormat? = null
    private var numberPicker: NumberPicker? = null

    constructor(value: Double, minValue: Double, maxValue: Double, step: Double, decimalFormat: DecimalFormat) : this() {
        this.value = value
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.decimalFormat = decimalFormat
    }

    constructor(inputDouble: InputDouble) : this() {
        this.value = inputDouble.value
        this.minValue = inputDouble.minValue
        this.maxValue = inputDouble.maxValue
        this.step = inputDouble.step
        this.decimalFormat = inputDouble.decimalFormat
    }

    override fun addToLayout(root: LinearLayout) {
        numberPicker = NumberPicker(root.context, null)
        numberPicker?.setParams(value, minValue, maxValue, step, decimalFormat, true, root.findViewById(R.id.ok))
        numberPicker?.setOnValueChangedListener { value: Double -> this.value = value }
        root.addView(numberPicker)
    }

    fun setValue(value: Double): InputDouble {
        this.value = value
        numberPicker?.value = value
        return this
    }
}
package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.NumberPicker
import java.text.DecimalFormat

class InputTempTarget(mainApp: MainApp) : Element(mainApp) {
    var units = Constants.MGDL
    var value = 0.0

    init {
        value = if (units == Constants.MMOL) 6.0 else 110.0
    }

    constructor(mainApp: MainApp, inputTempTarget: InputTempTarget) : this(mainApp) {
        value = inputTempTarget.value
        units = inputTempTarget.units
    }

    override fun addToLayout(root: LinearLayout) {
        var minValue = 0.0
        var maxValue = 0.0
        var step = 0.0
        var decimalFormat: DecimalFormat? = null
        if (units == Constants.MMOL) { // mmol
            minValue = Constants.MIN_TT_MMOL
            maxValue = Constants.MAX_TT_MMOL
            step = 0.1
            decimalFormat = DecimalFormat("0.0")
        } else { // mg/dL
            minValue = Constants.MIN_TT_MGDL
            maxValue = Constants.MAX_TT_MGDL
            step = 1.0
            decimalFormat = DecimalFormat("0")
        }
        val numberPicker = NumberPicker(root.context, null)
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, root.findViewById(R.id.ok))
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value }
        root.addView(numberPicker)
    }
}
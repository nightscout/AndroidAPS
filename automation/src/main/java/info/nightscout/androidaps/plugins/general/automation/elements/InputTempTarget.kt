package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputTempTarget(profileFunction: ProfileFunction) : Element() {
    var units: GlucoseUnit = GlucoseUnit.MGDL
    var value = 0.0

    init {
        units = profileFunction.getUnits()
        value = if (units == GlucoseUnit.MMOL) 6.0 else 110.0
    }

    constructor(profileFunction: ProfileFunction, inputTempTarget: InputTempTarget) : this(profileFunction) {
        value = inputTempTarget.value
        units = inputTempTarget.units
    }

    override fun addToLayout(root: LinearLayout) {
        val minValue: Double
        val maxValue: Double
        val step: Double
        val decimalFormat: DecimalFormat?
        if (units == GlucoseUnit.MMOL) { // mmol
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
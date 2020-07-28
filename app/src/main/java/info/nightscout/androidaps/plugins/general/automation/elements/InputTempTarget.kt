package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat
import javax.inject.Inject

class InputTempTarget(injector: HasAndroidInjector) : Element(injector) {
    var units = Constants.MGDL
    var value = 0.0
    @Inject lateinit var profileFunction: ProfileFunction

    init {
        units = profileFunction.getUnits()
        value = if (units == Constants.MMOL) 6.0 else 110.0
    }

    constructor(injector: HasAndroidInjector, inputTempTarget: InputTempTarget) : this(injector) {
        value = inputTempTarget.value
        units = inputTempTarget.units
    }

    override fun addToLayout(root: LinearLayout) {
        var minValue: Double
        var maxValue: Double
        var step: Double
        var decimalFormat: DecimalFormat?
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
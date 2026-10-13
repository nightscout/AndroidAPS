package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputTempTarget(profileFunction: ProfileFunction) : Element {

    var units: GlucoseUnit = GlucoseUnit.MGDL
    var value = 0.0

    init {
        units = profileFunction.getUnits()
        value = if (units == GlucoseUnit.MMOL) 6.0 else 110.0
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
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, minValue, maxValue, step, decimalFormat, true, root.findViewById(app.aaps.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL

            })
    }
}
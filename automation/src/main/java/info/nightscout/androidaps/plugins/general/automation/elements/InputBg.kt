package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputBg(profileFunction: ProfileFunction) : Element() {

    var units = GlucoseUnit.MGDL
    var value = 0.0
    var minValue = 0.0
    private var maxValue = 0.0
    private var step = 0.0
    private var decimalFormat: DecimalFormat? = null

    constructor(profileFunction: ProfileFunction, value: Double, units: GlucoseUnit) : this(profileFunction) {
        setUnits(units)
        this.value = value
    }

    init {
        setUnits(profileFunction.getUnits())
    }

    override fun addToLayout(root: LinearLayout) {
        val numberPicker = NumberPicker(root.context, null)
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, false, root.findViewById(R.id.ok))
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value }
        root.addView(numberPicker)
    }

    fun setValue(value: Double) : InputBg {
        this.value = value
        return this
    }

    fun setUnits(units: GlucoseUnit): InputBg {
        if (units == GlucoseUnit.MMOL) {
            minValue = MMOL_MIN
            maxValue = MMOL_MAX
            step = 0.1
            decimalFormat = DecimalFormat("0.0")
        } else {
            minValue = MGDL_MIN
            maxValue = MGDL_MAX
            step = 1.0
            decimalFormat = DecimalFormat("0")
        }
        this.units = units
        return this
    }

    companion object {
        const val MMOL_MIN = 3.0
        const val MMOL_MAX = 20.0
        const val MGDL_MIN = 54.0
        const val MGDL_MAX = 360.0
    }
}
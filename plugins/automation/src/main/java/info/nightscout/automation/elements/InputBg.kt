package info.nightscout.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import info.nightscout.core.ui.elements.NumberPicker
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.profile.ProfileFunction
import java.text.DecimalFormat

class InputBg(profileFunction: ProfileFunction) : Element {

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
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, minValue, maxValue, step, decimalFormat, false, root.findViewById(info.nightscout.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }

    fun setValue(value: Double): InputBg {
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
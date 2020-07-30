package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat
import javax.inject.Inject

class InputBg(injector: HasAndroidInjector) : Element(injector) {
    @Inject lateinit var profileFunction: ProfileFunction

    var units = Constants.MGDL
    var value = 0.0
    var minValue = 0.0
    private var maxValue = 0.0
    private var step = 0.0
    private var decimalFormat: DecimalFormat? = null

    constructor(injector: HasAndroidInjector, value: Double, units: String) : this(injector) {
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

    fun setUnits(units: String): InputBg {
        if (units == Constants.MMOL) {
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
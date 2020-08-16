package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputPercent(injector: HasAndroidInjector) : Element(injector) {
    var value: Double = 100.0

    constructor(injector: HasAndroidInjector, value: Double) : this(injector) {
        this.value = value
    }

    override fun addToLayout(root: LinearLayout) {
        val numberPicker = NumberPicker(root.context, null)
        numberPicker.setParams(100.0, MIN, MAX, 5.0, DecimalFormat("0"), true, root.findViewById(R.id.ok))
        numberPicker.value = value
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value }
        root.addView(numberPicker)
    }

    companion object {
        const val MIN = 70.0
        const val MAX = 130.0
    }
}
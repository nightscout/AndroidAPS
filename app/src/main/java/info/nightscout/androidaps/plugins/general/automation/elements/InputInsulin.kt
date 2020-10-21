package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputInsulin(injector: HasAndroidInjector) : Element(injector) {
    var value = 0.0

    constructor(injector: HasAndroidInjector, another: InputInsulin) : this(injector) {
        value = another.value
    }

    override fun addToLayout(root: LinearLayout) {
        val numberPicker = NumberPicker(root.context, null)
        numberPicker.setParams(0.0, -20.0, 20.0, 0.1, DecimalFormat("0.0"), true, root.findViewById(R.id.ok))
        numberPicker.value = value
        numberPicker.setOnValueChangedListener { value: Double -> this.value = value }
        root.addView(numberPicker)
    }
}
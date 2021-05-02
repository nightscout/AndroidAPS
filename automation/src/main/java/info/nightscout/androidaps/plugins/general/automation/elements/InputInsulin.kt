package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.utils.ui.NumberPicker
import java.text.DecimalFormat

class InputInsulin() : Element() {
    var value = 0.0

    constructor(another: InputInsulin) : this() {
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
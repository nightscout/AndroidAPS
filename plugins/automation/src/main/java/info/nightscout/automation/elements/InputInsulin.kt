package info.nightscout.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import info.nightscout.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputInsulin() : Element {

    var value = 0.0

    constructor(another: InputInsulin) : this() {
        value = another.value
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, -20.0, 20.0, 0.1, DecimalFormat("0.0"), true, root.findViewById(info.nightscout.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
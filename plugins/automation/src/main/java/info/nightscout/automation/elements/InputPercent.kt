package info.nightscout.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import info.nightscout.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputPercent() : Element() {

    var value: Double = 100.0

    constructor(value: Double) : this() {
        this.value = value
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, MIN, MAX, 5.0, DecimalFormat("0"), true, root.findViewById(info.nightscout.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }

    companion object {

        const val MIN = 50.0
        const val MAX = 130.0
    }
}
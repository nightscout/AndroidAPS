package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import app.aaps.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputWeight() : Element {

    // @Inject lateinit var sp: SP
    var value = 0.0 // not working on app start: sp.getDouble(R.string.key_bgAccel_ISF_weight, 0.0)

    constructor(another: Double) : this() {
        value = another
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, 0.0, 1.0, 0.05, DecimalFormat("0.00"), true, root.findViewById(app.aaps.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
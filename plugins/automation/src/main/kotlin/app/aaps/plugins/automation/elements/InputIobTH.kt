package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import app.aaps.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputIobTH() : Element {

    // @Inject lateinit var sp: SP
    var value = 100.0 // not working on app start: sp.getDouble(R.string.key_bgAccel_ISF_weight, 0.0)

    constructor(another: Double) : this() {
        value = another
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value, 10.0, 100.0, 5.0, DecimalFormat("0"), false, root.findViewById(app.aaps.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
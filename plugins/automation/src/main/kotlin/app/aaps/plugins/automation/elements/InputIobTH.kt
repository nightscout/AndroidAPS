package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.widget.LinearLayout
import app.aaps.core.ui.elements.NumberPicker
import java.text.DecimalFormat

class InputIobTH() : Element {

    // @Inject lateinit var sp: SP
    var value = 100 // not working on app start: sp.getDouble(R.string.key_iob...., 100.0)

    constructor(another: Int) : this() {
        value = another
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            NumberPicker(root.context, null).also {
                it.setParams(value.toDouble(), 10.0, 100.0, 5.0, DecimalFormat("0"), false, root.findViewById(app.aaps.core.ui.R.id.ok))
                it.setOnValueChangedListener { v: Double -> value = v.toInt() }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
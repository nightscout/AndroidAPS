package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R

class InputDropdownOnOffMenu(private val rh: ResourceHelper) : Element {

    var value: Boolean = true

    constructor(rh: ResourceHelper, state: Boolean) : this(rh) {
        value = state
    }

    @Suppress("unused")
    constructor(rh: ResourceHelper, another: InputDropdownOnOffMenu) : this(rh) {
        value = another.value
    }

    override fun addToLayout(root: LinearLayout) {
        val onOff = arrayListOf<CharSequence>(rh.gs(app.aaps.plugins.automation.R.string.on), rh.gs(app.aaps.plugins.automation.R.string.off))
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, R.layout.spinner_centered, onOff).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                    it.setMargins(0, rh.dpToPx(4), 0, rh.dpToPx(4))
                }

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        setValue(fromTextValue(onOff[position].toString()))
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                gravity = Gravity.CENTER_HORIZONTAL
                for (i in 0 until onOff.size) if (onOff[i] == toTextValue()) setSelection(i)
            })
    }

    fun toTextValue() = when (value) {
        true  -> rh.gs(app.aaps.plugins.automation.R.string.on)
        false -> rh.gs(app.aaps.plugins.automation.R.string.off)
    }

    private fun fromTextValue(textValue: String) = when (textValue) {
        rh.gs(app.aaps.plugins.automation.R.string.on)  -> true
        rh.gs(app.aaps.plugins.automation.R.string.off) -> false

        else                                            -> error("Invalid value")
    }

    fun setValue(state: Boolean): InputDropdownOnOffMenu {
        value = state
        return this
    }
}
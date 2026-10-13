package app.aaps.plugins.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.R

class InputLocationMode(private val rh: ResourceHelper) : Element {

    enum class Mode {
        INSIDE, OUTSIDE, GOING_IN, GOING_OUT;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                INSIDE    -> R.string.location_inside
                OUTSIDE   -> R.string.location_outside
                GOING_IN  -> R.string.location_going_in
                GOING_OUT -> R.string.location_going_out
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in Mode.entries) {
                    list.add(rh.gs(c.stringRes))
                }
                return list
            }
        }
    }

    var value: Mode = Mode.INSIDE

    constructor(rh: ResourceHelper, value: Mode) : this(rh) {
        this.value = value
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, app.aaps.core.ui.R.layout.spinner_centered, Mode.labels(rh)).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val spinnerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, rh.dpToPx(4), 0, rh.dpToPx(4))
                }
                layoutParams = spinnerParams
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        value = Mode.entries.toTypedArray()[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                setSelection(value.ordinal)
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
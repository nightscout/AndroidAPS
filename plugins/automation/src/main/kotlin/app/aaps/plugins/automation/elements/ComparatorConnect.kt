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

class ComparatorConnect(private val rh: ResourceHelper) : Element {

    enum class Compare {
        ON_CONNECT, ON_DISCONNECT;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                ON_CONNECT -> R.string.onconnect
                ON_DISCONNECT -> R.string.ondisconnect
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in values()) list.add(rh.gs(c.stringRes))
                return list
            }
        }
    }

    constructor(rh: ResourceHelper, value: Compare) : this(rh) {
        this.value = value
    }

    var value = Compare.ON_CONNECT

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, app.aaps.core.ui.R.layout.spinner_centered, Compare.labels(rh)).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, rh.dpToPx(4), 0, rh.dpToPx(4))
                }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        value = Compare.values()[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                setSelection(value.ordinal)
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
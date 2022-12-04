package info.nightscout.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.StringRes
import info.nightscout.automation.R
import info.nightscout.shared.interfaces.ResourceHelper

class Comparator(private val rh: ResourceHelper) : Element() {

    enum class Compare {
        IS_LESSER,
        IS_EQUAL_OR_LESSER,
        IS_EQUAL,
        IS_EQUAL_OR_GREATER,
        IS_GREATER,
        IS_NOT_AVAILABLE;

        @get:StringRes val stringRes: Int
            get() = when (this) {
                IS_LESSER           -> R.string.islesser
                IS_EQUAL_OR_LESSER  -> R.string.isequalorlesser
                IS_EQUAL            -> R.string.isequal
                IS_EQUAL_OR_GREATER -> R.string.isequalorgreater
                IS_GREATER          -> R.string.isgreater
                IS_NOT_AVAILABLE    -> R.string.isnotavailable
            }

        fun <T : Comparable<T>> check(obj1: T, obj2: T): Boolean {
            val comparison = obj1.compareTo(obj2)
            return when (this) {
                IS_LESSER           -> comparison < 0
                IS_EQUAL_OR_LESSER  -> comparison <= 0
                IS_EQUAL            -> comparison == 0
                IS_EQUAL_OR_GREATER -> comparison >= 0
                IS_GREATER          -> comparison > 0
                else                -> false
            }
        }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (c in values()) {
                    list.add(rh.gs(c.stringRes))
                }
                return list
            }
        }
    }

    constructor(rh: ResourceHelper, value: Compare) : this(rh) {
        this.value = value
    }

    var value = Compare.IS_EQUAL

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, info.nightscout.core.ui.R.layout.spinner_centered, Compare.labels(rh)).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, rh.dpToPx(1), 0, rh.dpToPx(1))
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

    fun setValue(compare: Compare): Comparator {
        value = compare
        return this
    }
}
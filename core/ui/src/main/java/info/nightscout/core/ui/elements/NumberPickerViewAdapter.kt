package info.nightscout.core.ui.elements

import info.nightscout.core.ui.databinding.NumberPickerLayoutBinding
import info.nightscout.core.ui.databinding.NumberPickerLayoutVerticalBinding

/**
 * NumberPickerViewAdapter binds both NumberPickerLayoutBinding and NumberPickerLayoutVerticalBinding shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class NumberPickerViewAdapter(
    val nH: NumberPickerLayoutBinding?,
    nV: NumberPickerLayoutVerticalBinding?,
) {

    init {
        if (nH == null && nV == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    val editText = nH?.display ?: nV?.display ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val minusButton = nH?.decrement ?: nV?.decrement ?: throw IllegalArgumentException("require at least on Binding parameter decrement")
    val plusButton = nH?.increment ?: nV?.increment ?: throw IllegalArgumentException("require at least on Binding parameter increment")
    var textInputLayout = nH?.textInputLayout ?: nV?.textInputLayout ?: throw IllegalArgumentException("require at least on Binding parameter textInputLayout")

    companion object {

        fun getBinding(bindLayout: NumberPickerLayoutBinding): NumberPickerViewAdapter {
            return NumberPickerViewAdapter(bindLayout, null)
        }

        fun getBinding(bindLayout: NumberPickerLayoutVerticalBinding): NumberPickerViewAdapter {
            return NumberPickerViewAdapter(null, bindLayout)
        }
    }
}

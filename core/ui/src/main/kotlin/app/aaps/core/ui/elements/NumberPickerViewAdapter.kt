package app.aaps.core.ui.elements

import app.aaps.core.ui.databinding.NumberPickerLayoutBinding
import app.aaps.core.ui.databinding.NumberPickerLayoutVerticalBinding

/**
 * NumberPickerViewAdapter binds both NumberPickerLayoutBinding and NumberPickerLayoutVerticalBinding shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class NumberPickerViewAdapter(nH: NumberPickerLayoutBinding?, nV: NumberPickerLayoutVerticalBinding?) {

    init {
        require(nH != null || nV != null) { "Require at least on Binding parameter" }
    }

    val editText = nH?.display ?: nV?.display ?: throw IllegalArgumentException("Missing require View Binding parameter display")
    val minusButton = nH?.decrement ?: nV?.decrement ?: throw IllegalArgumentException("require at least on Binding parameter decrement")
    val plusButton = nH?.increment ?: nV?.increment ?: throw IllegalArgumentException("require at least on Binding parameter increment")
    var textInputLayout = nH?.textInputLayout ?: nV?.textInputLayout ?: throw IllegalArgumentException("require at least on Binding parameter textInputLayout")

    companion object {

        fun getBinding(bindLayout: NumberPickerLayoutBinding): NumberPickerViewAdapter = NumberPickerViewAdapter(bindLayout, null)
        fun getBinding(bindLayout: NumberPickerLayoutVerticalBinding): NumberPickerViewAdapter = NumberPickerViewAdapter(null, bindLayout)
    }
}

package app.aaps.core.ui.elements

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import app.aaps.core.ui.databinding.NumberPickerLayoutVerticalBinding

class NumberPickerVertical(context: Context, attrs: AttributeSet? = null) : NumberPicker(context, attrs) {

    override fun inflate(context: Context) {
        val inflater = LayoutInflater.from(context)
        val bindLayout = NumberPickerLayoutVerticalBinding.inflate(inflater, this, true)
        binding = NumberPickerViewAdapter(null, bindLayout)
    }
}

package info.nightscout.androidaps.utils

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ui.NumberPicker

class NumberPickerVertical : NumberPicker {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    override fun inflate(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.number_picker_layout_vertical, this, true)
    }
}
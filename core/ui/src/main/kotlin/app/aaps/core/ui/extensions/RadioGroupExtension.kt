package app.aaps.core.ui.extensions

import android.widget.RadioGroup
import androidx.appcompat.widget.AppCompatRadioButton

fun RadioGroup.setSelection(index: Int) {
    (this.getChildAt(index) as AppCompatRadioButton).isChecked = true
}

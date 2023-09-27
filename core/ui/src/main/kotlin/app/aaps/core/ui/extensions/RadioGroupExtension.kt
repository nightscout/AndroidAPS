package app.aaps.core.ui.extensions

import android.widget.RadioGroup
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.view.forEach

val RadioGroup.selectedItemPosition: Int
    get() = this.indexOfChild(this.findViewById(this.checkedRadioButtonId))

fun RadioGroup.setSelection(index: Int) {
    (this.getChildAt(index) as AppCompatRadioButton).isChecked = true
}

fun RadioGroup.setEnableForChildren(state : Boolean) {
    forEach { child -> child.isEnabled = state}
}
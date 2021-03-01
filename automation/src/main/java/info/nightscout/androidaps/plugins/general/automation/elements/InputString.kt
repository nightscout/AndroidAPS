package info.nightscout.androidaps.plugins.general.automation.elements

import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import dagger.android.HasAndroidInjector

class InputString(injector: HasAndroidInjector) : Element(injector) {
    var textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            value = s.toString()
        }
    }
    var value = ""

    constructor(injector: HasAndroidInjector, value: String) : this(injector) {
        this.value = value
    }

    override fun addToLayout(root: LinearLayout) {
        val editText = EditText(root.context)
        editText.setText(value)
        editText.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        editText.addTextChangedListener(textWatcher)
        root.addView(editText)
    }
}
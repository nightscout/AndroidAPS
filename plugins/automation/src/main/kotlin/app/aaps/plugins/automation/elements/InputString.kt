package app.aaps.plugins.automation.elements

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

class InputString(var value: String = "") : Element {

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            value = s.toString()
        }
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            EditText(root.context).apply {
                setText(value)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addTextChangedListener(textWatcher)
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
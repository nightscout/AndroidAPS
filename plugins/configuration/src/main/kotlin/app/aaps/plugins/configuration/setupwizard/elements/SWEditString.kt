package app.aaps.plugins.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector

class SWEditString(injector: HasAndroidInjector) : SWItem(injector, Type.STRING) {

    private var validator: ((string: String) -> Boolean)? = null
    private var updateDelay = 0L

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val l = TextView(context)
        l.id = View.generateViewId()
        label?.let { l.setText(it) }
        l.setTypeface(l.typeface, Typeface.BOLD)
        layout.addView(l)
        val c = TextView(context)
        c.id = View.generateViewId()
        comment?.let { c.setText(it) }
        c.setTypeface(c.typeface, Typeface.ITALIC)
        layout.addView(c)
        val editText = EditText(context)
        editText.id = View.generateViewId()
        editText.inputType = InputType.TYPE_CLASS_TEXT
        editText.maxLines = 1
        editText.setText(sp.getString(preferenceId, ""))
        layout.addView(editText)
        super.generateDialog(layout)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (validator?.invoke(s.toString()) == true) save(s.toString(), updateDelay)
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    fun preferenceId(preferenceId: Int): SWEditString {
        this.preferenceId = preferenceId
        return this
    }

    fun validator(validator: (string: String) -> Boolean): SWEditString {
        this.validator = validator
        return this
    }

    fun updateDelay(updateDelay: Long): SWEditString {
        this.updateDelay = updateDelay
        return this
    }
}
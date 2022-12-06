package info.nightscout.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.setupwizard.events.EventSWLabel

class SWEditUrl(injector: HasAndroidInjector) : SWItem(injector, Type.URL) {
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
                if (Patterns.WEB_URL.matcher(s).matches()) save(s.toString(), updateDelay) else rxBus.send(EventSWLabel(rh.gs(info.nightscout.core.validators.R.string.error_url_not_valid)))
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    fun preferenceId(preferenceId: Int): SWEditUrl {
        this.preferenceId = preferenceId
        return this
    }

    fun updateDelay(updateDelay: Long): SWEditUrl {
        this.updateDelay = updateDelay
        return this
    }
}
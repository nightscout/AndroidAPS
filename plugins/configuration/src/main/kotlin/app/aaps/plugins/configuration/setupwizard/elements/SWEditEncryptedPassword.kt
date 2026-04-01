package app.aaps.plugins.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.R
import app.aaps.core.ui.extensions.scanForActivity
import app.aaps.core.ui.extensions.toVisibility
import javax.inject.Inject

class SWEditEncryptedPassword @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck, private val cryptoUtil: CryptoUtil) :
    SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var validator: (String) -> Boolean = String::isNotEmpty
    private var updateDelay = 0L

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val isPasswordSet = preferences.getIfExists(StringKey.ProtectionMasterPassword).isNullOrEmpty().not()
        var editText: EditText? = null
        var editText2: EditText? = null
        var l: TextView? = null
        var c: TextView? = null
        var c2: TextView? = null

        val button = Button(context)
        button.setText(R.string.unlock_settings)
        button.setOnClickListener {
            context.scanForActivity()?.let { activity ->
                passwordCheck.queryPassword(activity, app.aaps.core.keys.R.string.master_password, StringKey.ProtectionMasterPassword, {
                    button.visibility = View.GONE
                    editText?.visibility = View.VISIBLE
                    editText2?.visibility = View.VISIBLE
                    l?.visibility = View.VISIBLE
                    c?.visibility = View.VISIBLE
                    c2?.visibility = View.VISIBLE
                })
            }
        }
        button.visibility = isPasswordSet.toVisibility()
        layout.addView(button)

        label?.let {
            l = TextView(context)
            l.id = View.generateViewId()
            l.setText(it)
            l.setTypeface(l.typeface, Typeface.BOLD)
            layout.addView(l)
        }

        comment?.let {
            c = TextView(context)
            c.id = View.generateViewId()
            c.setText(it)
            c.setTypeface(c.typeface, Typeface.ITALIC)
            c.visibility = isPasswordSet.not().toVisibility()
            layout.addView(c)
        }

        editText = EditText(context)
        editText.id = View.generateViewId()
        editText.inputType = InputType.TYPE_CLASS_TEXT
        editText.maxLines = 1
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editText.visibility = isPasswordSet.not().toVisibility()
        layout.addView(editText)

        c2 = TextView(context)
        c2.id = View.generateViewId()
        c2.setText(R.string.confirm)
        c2.visibility = isPasswordSet.not().toVisibility()
        layout.addView(c2)

        editText2 = EditText(context)
        editText2.id = View.generateViewId()
        editText2.inputType = InputType.TYPE_CLASS_TEXT
        editText2.maxLines = 1
        editText2.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editText2.visibility = isPasswordSet.not().toVisibility()
        layout.addView(editText2)

        super.generateDialog(layout)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                preferences.remove(preference as StringPreferenceKey)
                scheduleChange(updateDelay)
                if (validator.invoke(editText.text.toString()) && validator.invoke(editText2.text.toString()) && editText.text.toString() == editText2.text.toString())
                    save(s.toString(), updateDelay)
            }

            override fun afterTextChanged(s: Editable) {}
        }
        editText.addTextChangedListener(watcher)
        editText2.addTextChangedListener(watcher)
    }

    fun preference(preference: StringKey): SWEditEncryptedPassword {
        this.preference = preference
        return this
    }

    override fun save(value: CharSequence, updateDelay: Long) {
        preferences.put(preference as StringPreferenceKey, cryptoUtil.hashPassword(value.toString()))
        scheduleChange(updateDelay)
    }
}
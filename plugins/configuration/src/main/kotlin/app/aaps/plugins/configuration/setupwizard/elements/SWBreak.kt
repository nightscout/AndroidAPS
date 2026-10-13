package app.aaps.plugins.configuration.setupwizard.elements

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Inject

class SWBreak @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textId: Int = 0
    private var visibilityValidator: (() -> Boolean)? = null

    fun visibility(visibilityValidator: () -> Boolean): SWBreak {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        layout.context
        val textView = TextView(layout.context)
        textView.id = View.generateViewId()
        textId = textView.id
        textView.text = "\n"
        layout.addView(textView)
    }

    override fun processVisibility(activity: AppCompatActivity) {
        val textView = activity.findViewById<TextView>(textId)
        textView?.visibility = if (visibilityValidator?.invoke() == false) View.GONE else View.VISIBLE
    }
}
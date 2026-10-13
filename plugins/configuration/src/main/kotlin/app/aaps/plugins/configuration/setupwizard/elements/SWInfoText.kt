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

class SWInfoText @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel: String? = null
    private var visibilityValidator: (() -> Boolean)? = null
    private var textId: Int = 0

    override fun label(label: Int): SWInfoText {
        this.label = label
        return this
    }

    fun label(newLabel: String): SWInfoText {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWInfoText {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val l = TextView(context)
        l.id = View.generateViewId()
        textId = l.id
        if (textLabel != null) l.text = textLabel else l.setText(label!!)
        layout.addView(l)
    }

    override fun processVisibility(activity: AppCompatActivity) {
        val l = activity.findViewById<TextView>(textId)
        if (visibilityValidator?.invoke() == false) l?.visibility = View.GONE else l?.visibility = View.VISIBLE
    }
}
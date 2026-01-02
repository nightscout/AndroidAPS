package app.aaps.plugins.configuration.setupwizard.elements

import android.text.util.Linkify
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import javax.inject.Inject

class SWHtmlLink @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel: String? = null
    private var textId: Int = 0
    private var visibilityValidator: (() -> Boolean)? = null

    override fun label(@StringRes label: Int): SWHtmlLink {
        this.label = label
        return this
    }

    fun label(newLabel: String): SWHtmlLink {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWHtmlLink {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val textView = TextView(context)
        textView.id = View.generateViewId()
        textId = textView.id
        textView.autoLinkMask = Linkify.WEB_URLS
        if (textLabel != null) textView.text = textLabel else textView.setText(label!!)
        layout.addView(textView)
    }

    override fun processVisibility(activity: AppCompatActivity) {
        val textView = activity.findViewById<TextView>(textId)
        textView?.visibility = if (visibilityValidator?.invoke() == false) View.GONE else View.VISIBLE
    }
}
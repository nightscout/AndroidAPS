package app.aaps.plugins.configuration.setupwizard

import android.annotation.SuppressLint
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventStatus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.configuration.setupwizard.elements.SWItem
import javax.inject.Inject

class SWEventListener @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    rxBus: RxBus,
    preferences: Preferences,
    passwordCheck: PasswordCheck
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var textLabel = 0
    private var status = ""
    private var textId: Int = 0
    private var visibilityValidator: (() -> Boolean)? = null

    lateinit var clazz: Class<out EventStatus>

    fun with(clazz: Class<out EventStatus>, swDefinition: SWDefinition): SWEventListener {
        this.clazz = clazz
        swDefinition.addListener(this)
        return this
    }

    override fun label(label: Int): SWEventListener {
        textLabel = label
        return this
    }

    fun initialStatus(status: String): SWEventListener {
        this.status = status
        return this
    }

    fun visibility(visibilityValidator: () -> Boolean): SWEventListener {
        this.visibilityValidator = visibilityValidator
        return this
    }

    @SuppressLint("SetTextI18n")
    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val textView = TextView(context)
        textView.id = View.generateViewId()
        textId = textView.id
        textView.text = (if (textLabel != 0) rh.gs(textLabel) else "") + " " + status
        layout.addView(textView)
    }

    override fun processVisibility(activity: AppCompatActivity) {
        val textView = activity.findViewById<TextView>(textId)
        if (visibilityValidator?.invoke() == false) textView?.visibility = View.GONE else textView?.visibility = View.VISIBLE
    }

    fun updateFromEvent(event: EventStatus, activity: AppCompatActivity) {
        if (event.javaClass.name == clazz.name) {
            activity.findViewById<TextView>(textId)?.let { textView ->
                status = event.getStatus(textView.context!!)
                @SuppressLint("SetTextI18n")
                textView.text = (if (textLabel != 0) rh.gs(textLabel) else "") + " " + status
            }
        }
    }
}
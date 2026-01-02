package app.aaps.plugins.configuration.setupwizard.elements

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.scanForActivity
import javax.inject.Inject

class SWButton @Inject constructor(aapsLogger: AAPSLogger, rh: ResourceHelper, rxBus: RxBus, preferences: Preferences, passwordCheck: PasswordCheck) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var buttonRunnable: Runnable? = null
    private var buttonText = 0
    private var buttonValidator: (() -> Boolean)? = null
    private var buttonId: Int = 0

    fun text(buttonText: Int): SWButton {
        this.buttonText = buttonText
        return this
    }

    fun action(buttonRunnable: Runnable): SWButton {
        this.buttonRunnable = buttonRunnable
        return this
    }

    fun visibility(buttonValidator: () -> Boolean): SWButton {
        this.buttonValidator = buttonValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val button = Button(context)
        button.id = View.generateViewId()
        buttonId = button.id
        button.setText(buttonText)
        button.setOnClickListener { buttonRunnable?.run() }
        processVisibility(layout.context.scanForActivity() ?: error("Activity not found"))
        layout.addView(button)
        super.generateDialog(layout)
    }

    override fun processVisibility(activity: AppCompatActivity) {
        val button = activity.findViewById<Button>(buttonId)
        if (buttonValidator?.invoke() == false) {
            button?.isEnabled = false
            button?.alpha = .5f
        } else {
            button?.isEnabled = true
            button?.alpha = 1f
        }
    }
}
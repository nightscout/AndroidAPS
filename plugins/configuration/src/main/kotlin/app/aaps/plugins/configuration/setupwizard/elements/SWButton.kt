package app.aaps.plugins.configuration.setupwizard.elements

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import dagger.android.HasAndroidInjector

class SWButton(injector: HasAndroidInjector) : SWItem(injector, Type.BUTTON) {

    private var buttonRunnable: Runnable? = null
    private var buttonText = 0
    private var buttonValidator: (() -> Boolean)? = { false }
    private var button: Button? = null

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
        button = Button(context)
        button?.setText(buttonText)
        button?.setOnClickListener { buttonRunnable?.run() }
        processVisibility()
        layout.addView(button)
        super.generateDialog(layout)
    }

    override fun processVisibility() {
        if (buttonValidator?.invoke() == false) button?.visibility = View.GONE
        else button?.visibility = View.VISIBLE
    }
}
package app.aaps.plugins.configuration.setupwizard.elements

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector

class SWBreak(injector: HasAndroidInjector) : SWItem(injector, Type.BREAK) {

    private var l: TextView? = null
    private var visibilityValidator: (() -> Boolean)? = null

    fun visibility(visibilityValidator: () -> Boolean): SWBreak {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        layout.context
        l = TextView(layout.context)
        l?.id = View.generateViewId()
        l?.text = "\n"
        layout.addView(l)
    }

    override fun processVisibility() {
        if (visibilityValidator?.invoke() == false) l?.visibility = View.GONE
        else l?.visibility = View.VISIBLE
    }
}
package info.nightscout.configuration.setupwizard.elements

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.setupwizard.SWValidator

class SWInfoText(injector: HasAndroidInjector) : SWItem(injector, Type.TEXT) {
    private var textLabel: String? = null
    private var l: TextView? = null
    private var visibilityValidator: SWValidator? = null

    override fun label(label: Int): SWInfoText {
        this.label = label
        return this
    }

    fun label(newLabel: String): SWInfoText {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: SWValidator): SWInfoText {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        l = TextView(context)
        l?.id = View.generateViewId()
        if (textLabel != null) l?.text = textLabel else l?.setText(label!!)
        layout.addView(l)
    }

    override fun processVisibility() {
        if (visibilityValidator != null && !visibilityValidator!!.isValid) l?.visibility = View.GONE else l?.visibility = View.VISIBLE
    }
}
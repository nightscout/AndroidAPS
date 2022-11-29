package info.nightscout.configuration.setupwizard.elements

import android.text.util.Linkify
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.setupwizard.SWValidator

class SWHtmlLink(injector: HasAndroidInjector) : SWItem(injector, Type.HTML_LINK) {

    private var textLabel: String? = null
    private var l: TextView? = null
    private var visibilityValidator: SWValidator? = null

    override fun label(@StringRes label: Int): SWHtmlLink {
        this.label = label
        return this
    }

    fun label(newLabel: String): SWHtmlLink {
        textLabel = newLabel
        return this
    }

    fun visibility(visibilityValidator: SWValidator): SWHtmlLink {
        this.visibilityValidator = visibilityValidator
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        l = TextView(context)
        l?.id = View.generateViewId()
        l?.autoLinkMask = Linkify.WEB_URLS
        if (textLabel != null) l?.text = textLabel else l?.setText(label!!)
        layout.addView(l)
    }

    override fun processVisibility() {
        if (visibilityValidator != null && !visibilityValidator!!.isValid) l?.visibility = View.GONE else l?.visibility = View.VISIBLE
    }
}
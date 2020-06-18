package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class LabelWithElement(injector: HasAndroidInjector) : Element(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper

    var element: Element? = null
    var textPre: String = ""
    var textPost: String = ""

    constructor(injector: HasAndroidInjector, textPre: String, textPost: String, element: Element) : this(injector) {
        this.textPre = textPre
        this.textPost = textPost
        this.element = element
    }

    override fun addToLayout(root: LinearLayout) { // container layout
        val layout = LinearLayout(root.context)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        // text view pre element
        var px = resourceHelper.dpToPx(10)
        val textViewPre = TextView(root.context)
        textViewPre.text = textPre
        textViewPre.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        //textViewPre.setWidth(MainApp.dpToPx(120));
        textViewPre.setPadding(px, px, px, px)
        textViewPre.setTypeface(textViewPre.typeface, Typeface.BOLD)
        layout.addView(textViewPre)
        val spacer = TextView(root.context)
        spacer.layoutParams = TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        layout.addView(spacer)
        // add element to layout
        element?.addToLayout(layout)
        // text view post element
        px = resourceHelper.dpToPx(5)
        val textViewPost = TextView(root.context)
        textViewPost.text = textPost
        textViewPost.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        //textViewPost.setWidth(MainApp.dpToPx(45));
        textViewPost.setPadding(px, px, px, px)
        textViewPost.setTypeface(textViewPost.typeface, Typeface.BOLD)
        layout.addView(textViewPost)
        // add layout to root layout
        root.addView(layout)
    }

}
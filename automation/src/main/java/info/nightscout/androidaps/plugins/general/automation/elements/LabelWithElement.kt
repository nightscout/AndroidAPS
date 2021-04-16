package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TextView
import info.nightscout.androidaps.utils.resources.ResourceHelper

class LabelWithElement(
    private val resourceHelper: ResourceHelper,
    var textPre: String = "",
    var textPost: String = "",
    var element: Element? = null,
) : Element() {

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
        textViewPost.setPadding(px, px, px, px)
        textViewPost.setTypeface(textViewPost.typeface, Typeface.BOLD)
        layout.addView(textViewPost)
        // add layout to root layout
        root.addView(layout)
    }

}
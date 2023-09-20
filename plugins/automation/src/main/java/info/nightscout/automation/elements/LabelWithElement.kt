package info.nightscout.automation.elements

import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.shared.interfaces.ResourceHelper

class LabelWithElement(
    private val rh: ResourceHelper,
    var textPre: String = "",
    var textPost: String = "",
    var element: Element? = null,
) : Element {

    override fun addToLayout(root: LinearLayout) { // container layout
        // text view pre element
        val px = rh.dpToPx(1)

        root.addView(
            TextView(root.context).apply {
                text = textPre
                setPadding(px, px, px, px)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
        )

        // add element to layout
        element?.addToLayout(root)
        // text view post element
        root.addView(
            TextView(root.context).apply {
                text = textPost
                setPadding(px, px, px, px)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
    }
}
package info.nightscout.androidaps.plugins.general.automation.elements

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import info.nightscout.androidaps.automation.R

class LayoutBuilder {

    var mElements = ArrayList<Element>()
    fun add(element: Element): LayoutBuilder {
        mElements.add(element)
        return this
    }

    fun maybeAdd(element: Element, condition: Boolean): LayoutBuilder {
        if (condition) mElements.add(element)
        return this
    }

    fun build(layout: LinearLayout) {
        val elementLayout = LinearLayout(layout.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dpToPx(layout.context, 2), dpToPx(layout.context, 2))
            }
            setBackgroundColor(layout.context.getColor(R.color.mdtp_line_dark))
        }
        for (e in mElements) {
            e.addToLayout(elementLayout)
        }
        layout.addView(elementLayout)
    }

    fun dpToPx(context: Context, dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
}
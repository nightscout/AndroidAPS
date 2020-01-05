package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger

class StaticLabel(mainApp: MainApp) : Element(mainApp) {
    var label = ""
    var trigger: Trigger? = null

    constructor(mainApp: MainApp, label: String, trigger: Trigger) : this(mainApp) {
        this.label = label
        this.trigger = trigger
    }

    constructor(mainApp: MainApp, resourceId: Int, trigger: Trigger) : this(mainApp) {
        label = resourceHelper.gs(resourceId)
        this.trigger = trigger
    }

    override fun addToLayout(root: LinearLayout) {
        val headerLayout = LinearLayout(root.context)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        headerLayout.setBackgroundColor(resourceHelper.gc(android.R.color.black))
        // text
        val px = resourceHelper.dpToPx(10)
        val textView = TextView(root.context)
        textView.text = label
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.weight = 1.0f
        textView.layoutParams = params
        textView.setPadding(px, px, px, px)
        textView.setTypeface(textView.typeface, Typeface.BOLD)
        headerLayout.addView(textView)
        trigger?.let {
            headerLayout.addView(it.createDeleteButton(root.context, it))
            headerLayout.addView(it.createCloneButton(root.context, it))
        }
        root.addView(headerLayout)
    }
}
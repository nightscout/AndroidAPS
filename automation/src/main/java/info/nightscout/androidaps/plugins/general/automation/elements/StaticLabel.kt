package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.utils.resources.ResourceHelper

class StaticLabel(private val resourceHelper: ResourceHelper) : Element() {

    var label = ""
    var trigger: Trigger? = null

    constructor(resourceHelper: ResourceHelper, label: String, trigger: Trigger) : this(resourceHelper) {
        this.label = label
        this.trigger = trigger
    }

    constructor(resourceHelper: ResourceHelper, resourceId: Int, trigger: Trigger) : this(resourceHelper) {
        label = resourceHelper.gs(resourceId)
        this.trigger = trigger
    }

    override fun addToLayout(root: LinearLayout) {
        val px = resourceHelper.dpToPx(10)
        root.addView(
            LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(resourceHelper.gc(android.R.color.black))
                addView(
                    TextView(root.context).apply {
                        text = label
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            weight = 1.0f
                        }
                        setPadding(px, px, px, px)
                        setTypeface(typeface, Typeface.BOLD)
                    })
                trigger?.let {
                    addView(it.createDeleteButton(root.context, it))
                    addView(it.createCloneButton(root.context, it))
                }
            })
    }
}
package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.core.R
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

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
        val headerLayout = LinearLayout(root.context)
        headerLayout.orientation = LinearLayout.HORIZONTAL
        headerLayout.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        headerLayout.setBackgroundColor(resourceHelper.getAttributeColor(null, R.attr.labelBackground))
        // text
        val px = resourceHelper.dpToPx(10)
        val textView = TextView(root.context)
        textView.text = label
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.weight = 1.0f
        textView.layoutParams = params
        textView.setPadding(px, px, px, px)
        textView.setTypeface(textView.typeface, Typeface.BOLD)
        textView.setTextColor(resourceHelper.getAttributeColor(null, R.attr.TitleAndLabelTextColor))
        headerLayout.addView(textView)
        trigger?.let {
            headerLayout.addView(it.createDeleteButton(root.context, it))
            headerLayout.addView(it.createCloneButton(root.context, it))
        }
        root.addView(headerLayout)
    }
}
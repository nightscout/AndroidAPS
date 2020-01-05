package info.nightscout.androidaps.plugins.general.automation.elements

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R

class StaticLabel(mainApp: MainApp) : Element(mainApp) {
    var label = ""

    constructor(mainApp: MainApp, label: String) : this(mainApp) {
        this.label = label
    }

    constructor(mainApp: MainApp, resourceId: Int) : this(mainApp) {
        label = resourceHelper.gs(resourceId)
    }

    override fun addToLayout(root: LinearLayout) { // text view pre element
        val px = resourceHelper.dpToPx(10)
        val textView = TextView(root.context)
        textView.text = label
        //       textViewPre.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,                ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(px, px, px, px)
        textView.setTypeface(textView.typeface, Typeface.BOLD)
        textView.setBackgroundColor(resourceHelper.gc(R.color.mdtp_line_dark))
        root.addView(textView)
    }
}
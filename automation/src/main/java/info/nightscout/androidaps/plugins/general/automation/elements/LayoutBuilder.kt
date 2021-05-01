package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout

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
        for (e in mElements) {
            e.addToLayout(layout)
        }
    }
}
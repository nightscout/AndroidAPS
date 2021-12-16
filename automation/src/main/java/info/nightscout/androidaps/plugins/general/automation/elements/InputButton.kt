package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

class InputButton() : Element() {

    var text: String? = null
    var runnable: Runnable? = null

    constructor(text: String, runnable: Runnable) : this() {
        this.text = text
        this.runnable = runnable
    }

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Button(root.context).apply {
                text = text
                setOnClickListener { runnable?.run() }
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
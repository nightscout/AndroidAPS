package info.nightscout.androidaps.plugins.general.automation.elements

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
        val button = Button(root.context)
        button.text = text
        button.setOnClickListener { runnable?.run() }
        root.addView(button)
    }
}
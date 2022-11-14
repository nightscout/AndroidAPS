package info.nightscout.automation.elements

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
            Button(root.context).also {
                it.text = text
                it.setOnClickListener { runnable?.run() }
                it.gravity = Gravity.CENTER_HORIZONTAL
            })
    }
}
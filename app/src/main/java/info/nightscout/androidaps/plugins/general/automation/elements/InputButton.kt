package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.Button
import android.widget.LinearLayout
import info.nightscout.androidaps.MainApp

class InputButton(mainApp: MainApp) : Element(mainApp) {
    var text: String? = null
    var runnable: Runnable? = null

    constructor(mainApp: MainApp, text: String, runnable: Runnable) : this(mainApp) {
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
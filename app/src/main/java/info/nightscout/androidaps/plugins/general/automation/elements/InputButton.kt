package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.Button
import android.widget.LinearLayout
import dagger.android.HasAndroidInjector

class InputButton(injector: HasAndroidInjector) : Element(injector) {
    var text: String? = null
    var runnable: Runnable? = null

    constructor(injector: HasAndroidInjector, text: String, runnable: Runnable) : this(injector) {
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
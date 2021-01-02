package info.nightscout.androidaps.plugins.general.automation.elements

import android.widget.LinearLayout
import dagger.android.HasAndroidInjector

abstract class Element(val injector: HasAndroidInjector) {

    abstract fun addToLayout(root: LinearLayout)

    init {
        injector.androidInjector().inject(this)
    }
}
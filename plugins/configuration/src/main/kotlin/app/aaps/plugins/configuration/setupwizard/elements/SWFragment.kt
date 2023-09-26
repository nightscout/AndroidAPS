package app.aaps.plugins.configuration.setupwizard.elements

import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import app.aaps.plugins.configuration.setupwizard.SWDefinition
import dagger.android.HasAndroidInjector

class SWFragment(injector: HasAndroidInjector, private var definition: SWDefinition) : SWItem(injector, Type.FRAGMENT) {

    lateinit var fragment: Fragment

    fun add(fragment: Fragment): SWFragment {
        this.fragment = fragment
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        definition.activity.supportFragmentManager.beginTransaction().add(layout.id, fragment, fragment.tag).commit()
    }
}
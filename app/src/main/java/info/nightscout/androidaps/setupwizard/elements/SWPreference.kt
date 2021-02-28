package info.nightscout.androidaps.setupwizard.elements

import android.os.Bundle
import android.widget.LinearLayout
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.setupwizard.SWDefinition
import javax.inject.Inject

class SWPreference(injector: HasAndroidInjector, private val definition: SWDefinition) : SWItem(injector, Type.PREFERENCE) {

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin

    private var xml: Int = -1

    fun option(xml: Int): SWPreference {
        this.xml = xml
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        addConfiguration(layout, xml)
        super.generateDialog(layout)
    }

    private fun addConfiguration(layout: LinearLayout, xml: Int) {
        MyPreferenceFragment().also { fragment ->
            fragment.arguments = Bundle().also { it.putInt("id", xml) }
            definition.activity.supportFragmentManager.beginTransaction().run {
                replace(layout.id, fragment)
                commit()
            }
        }
    }
}
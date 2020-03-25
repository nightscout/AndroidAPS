package info.nightscout.androidaps.setupwizard.elements

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.events.EventConfigBuilderChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.PluginStore
import info.nightscout.androidaps.setupwizard.events.EventSWUpdate
import javax.inject.Inject

class SWPlugin(injector: HasAndroidInjector) : SWItem(injector, Type.PLUGIN) {

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin

    private var pType: PluginType? = null
    private var radioGroup: RadioGroup? = null
    private var pluginDescription = 0
    private var makeVisible = true

    fun option(pType: PluginType, pluginDescription: Int): SWPlugin {
        this.pType = pType
        this.pluginDescription = pluginDescription
        return this
    }

    fun makeVisible(makeVisible: Boolean): SWPlugin {
        this.makeVisible = makeVisible
        return this
    }

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        radioGroup = RadioGroup(context)
        radioGroup?.clearCheck()
        val pluginsInCategory = pluginStore.getSpecificPluginsList(pType!!)
        radioGroup?.orientation = LinearLayout.VERTICAL
        radioGroup?.visibility = View.VISIBLE
        val pdesc = TextView(context)
        pdesc.setText(pluginDescription)
        var params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 40)
        pdesc.layoutParams = params
        layout.addView(pdesc)
        for (i in pluginsInCategory.indices) {
            val rdbtn = RadioButton(context)
            val p = pluginsInCategory[i]
            rdbtn.id = View.generateViewId()
            rdbtn.text = p.name
            if (p.isEnabled(pType!!)) rdbtn.isChecked = true
            rdbtn.tag = p
            radioGroup?.addView(rdbtn)
            params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(80, 0, 0, 0)
            val desc = TextView(context)
            desc.text = p.description
            desc.layoutParams = params
            radioGroup?.addView(desc)
        }
        radioGroup?.setOnCheckedChangeListener { group: RadioGroup, checkedId: Int ->
            val rb = group.findViewById<RadioButton>(checkedId)
            val plugin = rb.tag as PluginBase
            plugin.setPluginEnabled(pType!!, rb.isChecked)
            plugin.setFragmentVisible(pType!!, rb.isChecked && makeVisible)
            configBuilderPlugin.processOnEnabledCategoryChanged(plugin, pType)
            configBuilderPlugin.storeSettings("SetupWizard")
            rxBus.send(EventConfigBuilderChange())
            rxBus.send(EventSWUpdate(false))
        }
        layout.addView(radioGroup)
        super.generateDialog(layout)
    }
}
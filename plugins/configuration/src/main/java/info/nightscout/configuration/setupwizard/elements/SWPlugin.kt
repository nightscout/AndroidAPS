package info.nightscout.configuration.setupwizard.elements

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.setupwizard.SWDefinition
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.events.EventConfigBuilderChange
import info.nightscout.rx.events.EventSWUpdate
import javax.inject.Inject

class SWPlugin(injector: HasAndroidInjector, private val definition: SWDefinition) : SWItem(injector, Type.PLUGIN) {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var uiInteraction: UiInteraction

    private var pType: PluginType? = null
    private var radioGroup: RadioGroup? = null
    private var pluginDescription = 0
    private var makeVisible = true

    private var fragment: Fragment? = null

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
        var selectedPlugin: PluginBase? = null
        val context = layout.context
        radioGroup = RadioGroup(context)
        radioGroup?.clearCheck()
        val pluginsInCategory = activePlugin.getSpecificPluginsList(pType!!)
        radioGroup?.orientation = LinearLayout.VERTICAL
        radioGroup?.visibility = View.VISIBLE
        val pDesc = TextView(context)
        pDesc.setText(pluginDescription)
        var params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 40)
        pDesc.layoutParams = params
        layout.addView(pDesc)
        for (i in pluginsInCategory.indices) {
            val rdBtn = RadioButton(context)
            val p = pluginsInCategory[i]
            rdBtn.id = View.generateViewId()
            rdBtn.text = p.name
            if (p.isEnabled()) {
                rdBtn.isChecked = true
                selectedPlugin = p
            }
            rdBtn.tag = p
            radioGroup?.addView(rdBtn)
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
            configBuilder.processOnEnabledCategoryChanged(plugin, pType!!)
            configBuilder.storeSettings("SetupWizard")
            rxBus.send(EventConfigBuilderChange())
            rxBus.send(EventSWUpdate(false))
            addConfiguration(layout, plugin)
        }
        layout.addView(radioGroup)
        selectedPlugin?.let { addConfiguration(layout, it) }
        super.generateDialog(layout)
    }

    private fun addConfiguration(layout: LinearLayout, plugin: PluginBase) {
        if (plugin.preferencesId != -1) {
            fragment = Class.forName(uiInteraction.myPreferenceFragment.name).newInstance() as Fragment //MyPreferenceFragment()
            fragment?.let {
                it.arguments = Bundle().also { it.putInt("id", plugin.preferencesId) }
                definition.activity.supportFragmentManager.beginTransaction().run {
                    replace(layout.id, it)
                    commit()
                }
            }
        } else {
            definition.activity.supportFragmentManager.beginTransaction().run {
                fragment?.let { remove(it) }
                fragment = null
                commit()
            }
        }
    }
}
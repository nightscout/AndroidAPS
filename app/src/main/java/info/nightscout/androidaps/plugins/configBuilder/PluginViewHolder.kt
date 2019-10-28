package info.nightscout.androidaps.plugins.configBuilder

import android.content.Intent
import android.view.View
import android.widget.*
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.PreferencesActivity
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.PasswordProtection

class PluginViewHolder internal constructor(private val fragment: ConfigBuilderFragment,
                                            private val pluginType: PluginType,
                                            private val plugin: PluginBase) {

    val baseView: LinearLayout = fragment.layoutInflater.inflate(R.layout.configbuilder_single_plugin, null) as LinearLayout
    private val enabledExclusive: RadioButton
    private val enabledInclusive: CheckBox
    private val pluginName: TextView
    private val pluginDescription: TextView
    private val pluginPreferences: ImageButton
    private val pluginVisibility: CheckBox

    init {
        enabledExclusive = baseView.findViewById(R.id.plugin_enabled_exclusive)
        enabledInclusive = baseView.findViewById(R.id.plugin_enabled_inclusive)
        pluginName = baseView.findViewById(R.id.plugin_name)
        pluginDescription = baseView.findViewById(R.id.plugin_description)
        pluginPreferences = baseView.findViewById(R.id.plugin_preferences)
        pluginVisibility = baseView.findViewById(R.id.plugin_visibility)

        pluginVisibility.setOnClickListener {
            plugin.setFragmentVisible(pluginType, pluginVisibility.isChecked)
            ConfigBuilderPlugin.getPlugin().storeSettings("CheckedCheckboxVisible")
            RxBus.send(EventRebuildTabs())
            ConfigBuilderPlugin.getPlugin().logPluginStatus()
        }

        enabledExclusive.setOnClickListener {
            plugin.switchAllowed(if (enabledExclusive.visibility == View.VISIBLE) enabledExclusive.isChecked else enabledInclusive.isChecked, fragment.activity, pluginType)
        }
        enabledInclusive.setOnClickListener {
            plugin.switchAllowed(if (enabledExclusive.visibility == View.VISIBLE) enabledExclusive.isChecked else enabledInclusive.isChecked, fragment.activity, pluginType)
        }

        pluginPreferences.setOnClickListener {
            PasswordProtection.QueryPassword(fragment.context, R.string.settings_password, "settings_password", {
                val i = Intent(fragment.context, PreferencesActivity::class.java)
                i.putExtra("id", plugin.preferencesId)
                fragment.startActivity(i)
            }, null)
        }
        update()
    }

    fun update() {
        enabledExclusive.visibility = if (areMultipleSelectionsAllowed(pluginType)) View.GONE else View.VISIBLE
        enabledInclusive.visibility = if (areMultipleSelectionsAllowed(pluginType)) View.VISIBLE else View.GONE
        enabledExclusive.isChecked = plugin.isEnabled(pluginType)
        enabledInclusive.isChecked = plugin.isEnabled(pluginType)
        enabledInclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
        enabledExclusive.isEnabled = !plugin.pluginDescription.alwaysEnabled
        pluginName.text = plugin.name
        if (plugin.description == null)
            pluginDescription.visibility = View.GONE
        else {
            pluginDescription.visibility = View.VISIBLE
            pluginDescription.text = plugin.description
        }
        pluginPreferences.visibility = if (plugin.preferencesId == -1 || !plugin.isEnabled(pluginType)) View.INVISIBLE else View.VISIBLE
        pluginVisibility.visibility = if (plugin.hasFragment()) View.VISIBLE else View.INVISIBLE
        pluginVisibility.isEnabled = !(plugin.pluginDescription.neverVisible || plugin.pluginDescription.alwaysVisible) && plugin.isEnabled(pluginType)
        pluginVisibility.isChecked = plugin.isFragmentVisible
    }

    private fun areMultipleSelectionsAllowed(type: PluginType): Boolean {
        return type == PluginType.GENERAL || type == PluginType.CONSTRAINTS || type == PluginType.LOOP
    }

}

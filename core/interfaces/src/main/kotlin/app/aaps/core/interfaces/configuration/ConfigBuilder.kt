package app.aaps.core.interfaces.configuration

import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginType

interface ConfigBuilder {

    /**
     * Called during start of app to load configuration and start enabled plugins
     */
    fun initialize()

    /**
     * Store current configuration to SharedPreferences
     */
    fun storeSettings(from: String)

    /**
     * Enable another plugin and fragment and disable currently enabled if they are mutually exclusive
     */
    fun performPluginSwitch(changedPlugin: PluginBase, enabled: Boolean, type: PluginType)

    /**
     * Make sure plugins configuration is valid after enabling/disabling plugin
     */
    fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType)

    /**
     * Fill LinearLayout with list of available plugins and checkboxes for enabling/disabling
     *
     * @param title Paragraph title or null if provided elsewhere
     * @param description comment
     * @param pluginType plugin category for example SYNC
     * @param plugins list of plugins
     * @param pluginViewHolders links to created UI elements (for calling `update` if configuration is changed)
     * @param fragment
     * @param activity either fragment or activity must be non null
     * @param parent UI container to add views
     */
    fun createViewsForPlugins(
        @StringRes title: Int?,
        @StringRes description: Int,
        pluginType: PluginType,
        plugins: List<PluginBase>,
        pluginViewHolders: ArrayList<PluginViewHolderInterface>,
        fragment: Fragment? = null,
        activity: FragmentActivity? = null,
        parent: LinearLayout
    )

    interface PluginViewHolderInterface {

        fun update()
    }
}
package app.aaps.core.interfaces.configuration

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.plugin.PluginBase

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
     * @param activity activity
     * @param parent UI container to add views
     * @param showExpanded display as expanded on first view
     */
    fun createViewsForPlugins(
        @StringRes title: Int?,
        @StringRes description: Int,
        pluginType: PluginType,
        plugins: List<PluginBase>,
        pluginViewHolders: ArrayList<PluginViewHolderInterface>?,
        activity: FragmentActivity,
        parent: LinearLayout,
        showExpanded: Boolean = false
    )

    fun interface PluginViewHolderInterface {

        fun update(context: Context)
    }

    /**
     * Restart application
     */
    fun exitApp(from: String, source: Sources, launchAgain: Boolean)
}
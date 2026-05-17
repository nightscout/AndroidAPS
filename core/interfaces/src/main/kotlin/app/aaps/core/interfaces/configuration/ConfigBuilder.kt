package app.aaps.core.interfaces.configuration

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
     * Attempt to switch a plugin, handling pump-specific validation.
     * For pump plugins that are hardware pumps and haven't been allowed yet,
     * returns a warning message string that should be shown in a confirmation dialog.
     *
     * @return null if the switch was performed immediately, or a confirmation message
     *         if user must confirm before proceeding (call [confirmPumpPluginSwitch] on confirm).
     */
    fun requestPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType): String?

    /**
     * Confirm and perform a hardware pump plugin switch after the user acknowledged the warning.
     * Call this after the user confirms in response to [requestPluginSwitch] returning non-null.
     */
    fun confirmPumpPluginSwitch(plugin: PluginBase, enabled: Boolean, type: PluginType)

    /**
     * Make sure plugins configuration is valid after enabling/disabling plugin
     */
    fun processOnEnabledCategoryChanged(changedPlugin: PluginBase, type: PluginType)

    /**
     * Restart application
     */
    fun exitApp(from: String, source: Sources, launchAgain: Boolean)
}

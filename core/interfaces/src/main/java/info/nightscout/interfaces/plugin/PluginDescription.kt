package info.nightscout.interfaces.plugin

import info.nightscout.annotations.OpenForTesting

@OpenForTesting
class PluginDescription {

    var mainType = PluginType.GENERAL
    var fragmentClass: String? = null
    var alwaysVisible = false
    var neverVisible = false
    var alwaysEnabled = false
    var showInList = true
    var pluginName = -1
    var shortName = -1
    var description = -1
    var preferencesId = -1
    var enableByDefault = false
    var visibleByDefault = false
    var defaultPlugin = false
    var pluginIcon = -1
    var pluginIcon2 = -1

    fun mainType(mainType: PluginType): PluginDescription = this.also { it.mainType = mainType }
    fun fragmentClass(fragmentClass: String?): PluginDescription = this.also { it.fragmentClass = fragmentClass }
    fun alwaysEnabled(alwaysEnabled: Boolean): PluginDescription = this.also { it.alwaysEnabled = alwaysEnabled }
    fun alwaysVisible(alwaysVisible: Boolean): PluginDescription = this.also { it.alwaysVisible = alwaysVisible }
    fun neverVisible(neverVisible: Boolean): PluginDescription = this.also { it.neverVisible = neverVisible }
    fun showInList(showInList: Boolean): PluginDescription = this.also { it.showInList = showInList }
    fun pluginIcon(pluginIcon: Int): PluginDescription = this.also { it.pluginIcon = pluginIcon }
    fun pluginIcon2(pluginIcon2: Int): PluginDescription = this.also { it.pluginIcon2 = pluginIcon2 }
    fun pluginName(pluginName: Int): PluginDescription = this.also { it.pluginName = pluginName }
    fun shortName(shortName: Int): PluginDescription = this.also { it.shortName = shortName }
    fun preferencesId(preferencesId: Int): PluginDescription = this.also { it.preferencesId = preferencesId }
    fun enableByDefault(enableByDefault: Boolean): PluginDescription = this.also { it.enableByDefault = enableByDefault }
    fun visibleByDefault(visibleByDefault: Boolean): PluginDescription = this.also { it.visibleByDefault = visibleByDefault }
    fun description(description: Int): PluginDescription = this.also { it.description = description }
    fun setDefault(value: Boolean = true): PluginDescription = this.also { it.defaultPlugin = value }
}
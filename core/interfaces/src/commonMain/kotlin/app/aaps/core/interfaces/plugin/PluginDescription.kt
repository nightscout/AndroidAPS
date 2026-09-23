package app.aaps.core.interfaces.plugin

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.keys.interfaces.TextRef

open class PluginDescription {

    var mainType = PluginType.GENERAL

    /**
     * Compose content provider for plugins migrated to Jetpack Compose.
     * This is a lazy provider that creates the content only when needed, avoiding
     * early memory allocation for screens the user may never open.
     *
     * The returned object should implement ComposablePluginContent from core:ui.
     * Type is Any to avoid Compose dependency in core:interfaces.
     */
    var composeContentProvider: ((PluginBase) -> Any)? = null

    var alwaysEnabled = false
    var showInList = { true }
    var pluginName: TextRef? = null
    var shortName: TextRef? = null
    var description: TextRef? = null
    var defaultPlugin = false

    var icon: ImageVector? = null
    var preferencesVisibleInSimpleMode = true

    fun mainType(mainType: PluginType): PluginDescription = this.also { it.mainType = mainType }
    fun alwaysEnabled(alwaysEnabled: Boolean): PluginDescription = this.also { it.alwaysEnabled = alwaysEnabled }
    fun showInList(showInList: () -> Boolean): PluginDescription = this.also { it.showInList = showInList }

    fun icon(icon: ImageVector): PluginDescription = this.also { it.icon = icon }
    fun pluginName(pluginName: TextRef): PluginDescription = this.also { it.pluginName = pluginName }
    fun shortName(shortName: TextRef): PluginDescription = this.also { it.shortName = shortName }
    fun description(description: TextRef): PluginDescription = this.also { it.description = description }
    fun setDefault(value: Boolean = true): PluginDescription = this.also { it.defaultPlugin = value }
    fun preferencesVisibleInSimpleMode(value: Boolean): PluginDescription = this.also { it.preferencesVisibleInSimpleMode = value }
    fun composeContent(provider: (PluginBase) -> Any): PluginDescription = this.also { it.composeContentProvider = provider }
}

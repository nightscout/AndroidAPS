package app.aaps.core.interfaces.plugin

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.plugin.PluginType

open class PluginDescription {

    enum class Position { MENU, TAB }

    var mainType = PluginType.GENERAL

    @Deprecated("remove")
    var fragmentClass: String? = null

    /**
     * Compose content provider for plugins migrated to Jetpack Compose.
     * This is a lazy provider that creates the content only when needed, avoiding
     * early memory allocation for screens the user may never open.
     *
     * The returned object should implement ComposablePluginContent from core:ui.
     * Type is Any to avoid Compose dependency in core:interfaces.
     */
    var composeContentProvider: ((PluginBase) -> Any)? = null

    @Deprecated("remove")
    var alwaysVisible = false
    var neverVisible = false
    var alwaysEnabled = false
    var showInList = { true }
    var pluginName = -1
    var shortName = -1
    var description = -1
    var enableByDefault = false

    @Deprecated("remove")
    var visibleByDefault = false
    var defaultPlugin = false

    var icon: ImageVector? = null
    var preferencesVisibleInSimpleMode = true

    @Deprecated("remove")
    var simpleModePosition: Position = Position.MENU

    fun mainType(mainType: PluginType): PluginDescription = this.also { it.mainType = mainType }

    @Deprecated("remove")
    fun fragmentClass(fragmentClass: String?): PluginDescription = this.also { it.fragmentClass = fragmentClass }
    fun alwaysEnabled(alwaysEnabled: Boolean): PluginDescription = this.also { it.alwaysEnabled = alwaysEnabled }

    @Deprecated("remove")
    fun alwaysVisible(alwaysVisible: Boolean): PluginDescription = this.also { it.alwaysVisible = alwaysVisible }
    fun neverVisible(neverVisible: Boolean): PluginDescription = this.also { it.neverVisible = neverVisible }
    fun showInList(showInList: () -> Boolean): PluginDescription = this.also { it.showInList = showInList }

    fun icon(icon: ImageVector): PluginDescription = this.also { it.icon = icon }
    fun pluginName(pluginName: Int): PluginDescription = this.also { it.pluginName = pluginName }
    fun shortName(shortName: Int): PluginDescription = this.also { it.shortName = shortName }
    fun enableByDefault(enableByDefault: Boolean): PluginDescription = this.also { it.enableByDefault = enableByDefault }

    @Deprecated("remove")
    fun visibleByDefault(visibleByDefault: Boolean): PluginDescription = this.also { it.visibleByDefault = visibleByDefault }
    fun description(description: Int): PluginDescription = this.also { it.description = description }
    fun setDefault(value: Boolean = true): PluginDescription = this.also { it.defaultPlugin = value }
    fun preferencesVisibleInSimpleMode(value: Boolean): PluginDescription = this.also { it.preferencesVisibleInSimpleMode = value }

    @Deprecated("remove")
    fun simpleModePosition(value: Position = Position.MENU): PluginDescription = this.also { it.simpleModePosition = value }
    fun composeContent(provider: (PluginBase) -> Any): PluginDescription = this.also { it.composeContentProvider = provider }
}
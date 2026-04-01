package app.aaps.core.interfaces.plugin

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.plugin.PluginType

open class PluginDescription {

    enum class Position { MENU, TAB }

    companion object {

        /**
         * Plugin has no xml preferences
         */
        const val PREFERENCE_NONE = -1

        /**
         * Plugin generates [androidx.preference.PreferenceScreen] directly
         */
        const val PREFERENCE_SCREEN = 0
    }

    var mainType = PluginType.GENERAL
    var fragmentClass: String? = null

    /**
     * Compose content provider for plugins migrated to Jetpack Compose.
     * When set, SingleFragmentActivity will use this instead of fragmentClass.
     * This is a lazy provider that creates the content only when needed, avoiding
     * early memory allocation for screens the user may never open.
     *
     * The returned object should implement ComposablePluginContent from core:ui.
     * Type is Any to avoid Compose dependency in core:interfaces.
     */
    var composeContentProvider: ((PluginBase) -> Any)? = null
    var alwaysVisible = false
    var neverVisible = false
    var alwaysEnabled = false
    var showInList = { true }
    var pluginName = -1
    var shortName = -1
    var description = -1
    var preferencesId = PREFERENCE_NONE
    var enableByDefault = false
    var visibleByDefault = false
    var defaultPlugin = false

    @Deprecated("use icon")
    var pluginIcon = -1

    @Deprecated("use icon2")
    var pluginIcon2 = -1
    var icon: ImageVector? = null
    var icon2: ImageVector? = null
    var preferencesVisibleInSimpleMode = true
    var simpleModePosition: Position = Position.MENU

    fun mainType(mainType: PluginType): PluginDescription = this.also { it.mainType = mainType }
    fun fragmentClass(fragmentClass: String?): PluginDescription = this.also { it.fragmentClass = fragmentClass }
    fun alwaysEnabled(alwaysEnabled: Boolean): PluginDescription = this.also { it.alwaysEnabled = alwaysEnabled }
    fun alwaysVisible(alwaysVisible: Boolean): PluginDescription = this.also { it.alwaysVisible = alwaysVisible }
    fun neverVisible(neverVisible: Boolean): PluginDescription = this.also { it.neverVisible = neverVisible }
    fun showInList(showInList: () -> Boolean): PluginDescription = this.also { it.showInList = showInList }
    @Deprecated("use icon")
    fun pluginIcon(pluginIcon: Int): PluginDescription = this.also { it.pluginIcon = pluginIcon }
    @Deprecated("use icon2")
    fun pluginIcon2(pluginIcon2: Int): PluginDescription = this.also { it.pluginIcon2 = pluginIcon2 }
    fun icon(icon: ImageVector): PluginDescription = this.also { it.icon = icon }
    fun icon2(icon2: ImageVector): PluginDescription = this.also { it.icon2 = icon2 }
    fun pluginName(pluginName: Int): PluginDescription = this.also { it.pluginName = pluginName }
    fun shortName(shortName: Int): PluginDescription = this.also { it.shortName = shortName }
    fun preferencesId(preferencesId: Int): PluginDescription = this.also { it.preferencesId = preferencesId }
    fun enableByDefault(enableByDefault: Boolean): PluginDescription = this.also { it.enableByDefault = enableByDefault }
    fun visibleByDefault(visibleByDefault: Boolean): PluginDescription = this.also { it.visibleByDefault = visibleByDefault }
    fun description(description: Int): PluginDescription = this.also { it.description = description }
    fun setDefault(value: Boolean = true): PluginDescription = this.also { it.defaultPlugin = value }
    fun preferencesVisibleInSimpleMode(value: Boolean): PluginDescription = this.also { it.preferencesVisibleInSimpleMode = value }
    fun simpleModePosition(value: Position = Position.MENU): PluginDescription = this.also { it.simpleModePosition = value }
    fun composeContent(provider: (PluginBase) -> Any): PluginDescription = this.also { it.composeContentProvider = provider }
}
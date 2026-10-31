package app.aaps.core.ui.compose

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/**
 * Single source of truth for the plugin-category → title string mapping, shared by the Configuration
 * screen and the Quick-launch config. Exhaustive `when` (no `else`) so adding a [PluginType] is a
 * compile-forced decision here.
 */

fun pluginCategoryTitle(type: PluginType): TextRef = when (type) {
    PluginType.BGSOURCE    -> CoreUiStrings.configbuilder_bgsource
    PluginType.SMOOTHING   -> CoreUiStrings.configbuilder_smoothing
    PluginType.CALIBRATION -> CoreUiStrings.configbuilder_calibration
    PluginType.PUMP        -> CoreUiStrings.configbuilder_pump
    PluginType.SENSITIVITY -> CoreUiStrings.configbuilder_sensitivity
    PluginType.APS         -> CoreUiStrings.configbuilder_aps
    PluginType.LOOP        -> CoreUiStrings.configbuilder_loop
    PluginType.CONSTRAINTS -> CoreUiStrings.constraints
    PluginType.SYNC        -> CoreUiStrings.configbuilder_sync
    PluginType.GENERAL     -> CoreUiStrings.configbuilder_general
}

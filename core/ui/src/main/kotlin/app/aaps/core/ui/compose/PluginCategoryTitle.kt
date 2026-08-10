package app.aaps.core.ui.compose

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.UiStrings
import androidx.annotation.StringRes
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.ui.R

/**
 * Single source of truth for the plugin-category → title string mapping, shared by the Configuration
 * screen and the Quick-launch config. Exhaustive `when` (no `else`) so adding a [PluginType] is a
 * compile-forced decision here.
 */

fun pluginCategoryTitle(type: PluginType): TextRef = when (type) {
    PluginType.BGSOURCE    -> UiStrings.configbuilder_bgsource
    PluginType.SMOOTHING   -> UiStrings.configbuilder_smoothing
    PluginType.CALIBRATION -> UiStrings.configbuilder_calibration
    PluginType.PUMP        -> UiStrings.configbuilder_pump
    PluginType.SENSITIVITY -> UiStrings.configbuilder_sensitivity
    PluginType.APS         -> UiStrings.configbuilder_aps
    PluginType.LOOP        -> UiStrings.configbuilder_loop
    PluginType.CONSTRAINTS -> UiStrings.constraints
    PluginType.SYNC        -> UiStrings.configbuilder_sync
    PluginType.GENERAL     -> UiStrings.configbuilder_general
}

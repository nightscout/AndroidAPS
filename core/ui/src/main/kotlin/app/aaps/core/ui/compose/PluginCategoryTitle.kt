package app.aaps.core.ui.compose

import androidx.annotation.StringRes
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.ui.R

/**
 * Single source of truth for the plugin-category → title string mapping, shared by the Configuration
 * screen and the Quick-launch config. Exhaustive `when` (no `else`) so adding a [PluginType] is a
 * compile-forced decision here.
 */
@StringRes
fun pluginCategoryTitleRes(type: PluginType): Int = when (type) {
    PluginType.BGSOURCE    -> R.string.configbuilder_bgsource
    PluginType.SMOOTHING   -> R.string.configbuilder_smoothing
    PluginType.CALIBRATION -> R.string.configbuilder_calibration
    PluginType.PUMP        -> R.string.configbuilder_pump
    PluginType.SENSITIVITY -> R.string.configbuilder_sensitivity
    PluginType.APS         -> R.string.configbuilder_aps
    PluginType.LOOP        -> R.string.configbuilder_loop
    PluginType.CONSTRAINTS -> R.string.constraints
    PluginType.SYNC        -> R.string.configbuilder_sync
    PluginType.GENERAL     -> R.string.configbuilder_general
}

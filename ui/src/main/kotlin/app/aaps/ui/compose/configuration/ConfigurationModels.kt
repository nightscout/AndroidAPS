package app.aaps.ui.compose.configuration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.ui.compose.ConfigPluginUiModel

/**
 * Immutable UI model for a plugin category in the configuration screen.
 * Immutable category for the configuration screen UI layer.
 */
@Immutable
data class ConfigCategoryUiModel(
    val type: PluginType,
    val titleRes: Int,
    val plugins: List<ConfigPluginUiModel>,
    val isMultiSelect: Boolean,
    val subtitle: String,
    val categoryIcon: ImageVector?,
    val categoryIconRes: Int?
)

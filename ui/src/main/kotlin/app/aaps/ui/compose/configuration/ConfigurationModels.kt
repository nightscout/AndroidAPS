package app.aaps.ui.compose.configuration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.plugin.PluginType

/**
 * Immutable UI model for a plugin in the configuration screen.
 * All mutable PluginBase state is snapshotted at build time.
 */
@Immutable
data class ConfigPluginUiModel(
    val id: String,
    val name: String,
    val description: String?,
    val menuIcon: Int,
    val composeIcon: ImageVector?,
    val isEnabled: Boolean,
    val canToggle: Boolean,
    val showPreferences: Boolean
)

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

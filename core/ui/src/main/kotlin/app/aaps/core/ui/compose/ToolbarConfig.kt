package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

/**
 * Configuration for the toolbar in Compose screens.
 * Used by SingleFragmentActivity to render the toolbar for plugins with Compose content.
 *
 * @param title The title to display in the toolbar
 * @param navigationIcon The navigation icon composable (back arrow or close icon)
 * @param actions The action buttons to display in the toolbar
 */
data class ToolbarConfig(
    val title: String,
    val navigationIcon: @Composable () -> Unit,
    val actions: @Composable RowScope.() -> Unit
)

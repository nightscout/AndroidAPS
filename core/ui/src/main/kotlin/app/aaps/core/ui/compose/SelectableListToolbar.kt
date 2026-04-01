package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R

/**
 * Reusable toolbar builder for screens with selectable list items.
 * Supports two modes:
 * - Normal mode: Shows title, back button, and optional toggle/menu actions
 * - Selection mode: Shows selected count, close button, and delete action
 *
 * @param isRemovingMode Whether selection mode is active
 * @param selectedCount Number of items selected (shown in selection mode)
 * @param onExitRemovingMode Called when user exits selection mode
 * @param onNavigateBack Called when user presses back button (normal mode)
 * @param onDelete Called when user presses delete button (selection mode)
 * @param rh Resource helper for string resources
 * @param title Title to display in normal mode
 * @param showInvalidated Optional: Current state of show/hide invalidated toggle (null to hide toggle)
 * @param onToggleInvalidated Optional: Called when show/hide invalidated is toggled
 * @param showLoop Optional: Current state of show/hide loop toggle (null to hide toggle)
 * @param onToggleLoop Optional: Called when show/hide loop is toggled
 * @param onSettings Optional: Called when settings icon is clicked (null to hide icon)
 * @param menuItems Optional: List of dropdown menu items with label and onClick
 */
fun SelectableListToolbar(
    isRemovingMode: Boolean,
    selectedCount: Int,
    onExitRemovingMode: () -> Unit,
    onNavigateBack: () -> Unit,
    onDelete: () -> Unit,
    rh: ResourceHelper,
    title: String = "",
    showInvalidated: Boolean? = null,
    onToggleInvalidated: (() -> Unit)? = null,
    showLoop: Boolean? = null,
    onToggleLoop: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    menuItems: List<MenuItemData> = emptyList()
): ToolbarConfig {
    return if (isRemovingMode) {
        // Selection mode: show count, close icon, and delete action
        ToolbarConfig(
            title = rh.gs(R.string.count_selected, selectedCount),
            navigationIcon = {
                IconButton(onClick = onExitRemovingMode) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = rh.gs(R.string.close)
                    )
                }
            },
            actions = {
                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = rh.gs(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    } else {
        // Normal mode: show title, back icon, and optional actions
        ToolbarConfig(
            title = title,
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = rh.gs(R.string.back)
                    )
                }
            },
            actions = {
                // Show/Hide invalidated button (if provided)
                if (showInvalidated != null && onToggleInvalidated != null) {
                    IconButton(onClick = onToggleInvalidated) {
                        Icon(
                            imageVector = if (showInvalidated) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showInvalidated)
                                rh.gs(R.string.hide_invalidated)
                            else
                                rh.gs(R.string.show_invalidated)
                        )
                    }
                }

                // Show/Hide loop button (if provided)
                if (showLoop != null && onToggleLoop != null) {
                    IconButton(onClick = onToggleLoop) {
                        Icon(
                            imageVector = if (showLoop) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = rh.gs(R.string.show_hide_records)
                        )
                    }
                }

                // Dropdown menu (if menu items provided)
                if (menuItems.isNotEmpty()) {
                    MenuDropdown(menuItems = menuItems, rh = rh)
                }

                // Settings button (if provided)
                if (onSettings != null) {
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = rh.gs(R.string.nav_plugin_preferences)
                        )
                    }
                }
            }
        )
    }
}

/**
 * Composable for dropdown menu in toolbar
 */
@Composable
private fun MenuDropdown(
    menuItems: List<MenuItemData>,
    rh: ResourceHelper
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = rh.gs(R.string.more_options)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            menuItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        showMenu = false
                        item.onClick()
                    }
                )
            }
        }
    }
}

/**
 * Data class for dropdown menu items
 */
data class MenuItemData(
    val label: String,
    val onClick: () -> Unit
)

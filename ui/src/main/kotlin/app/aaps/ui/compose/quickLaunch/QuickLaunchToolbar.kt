package app.aaps.ui.compose.quickLaunch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.navigation.color

@Immutable
data class ResolvedQuickLaunchItem(
    val action: QuickLaunchAction,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val description: String? = null
)

/**
 * Floating pill-shaped toolbar for quick actions.
 * Displayed as an overlay at the bottom of the overview screen.
 * When items exceed available width, extra items go into an overflow dropdown menu.
 * Long-press any icon to see a tooltip with label and description.
 */
@Composable
fun QuickLaunchToolbar(
    items: List<ResolvedQuickLaunchItem>,
    onActionClick: (QuickLaunchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        tonalElevation = 6.dp,
        modifier = modifier.padding(horizontal = 16.dp)
    ) {
        BoxWithConstraints(Modifier.height(48.dp)) {
            val itemSizePx = with(LocalDensity.current) { 48.dp.toPx() }
            val contentPaddingPx = with(LocalDensity.current) { 8.dp.toPx() }
            val maxItems = ((constraints.maxWidth - contentPaddingPx) / itemSizePx).toInt()
                .coerceAtLeast(1)

            val needsOverflow = items.size > maxItems
            val visibleItems = if (needsOverflow) items.take((maxItems - 1).coerceAtLeast(0)) else items
            val overflowItems = if (needsOverflow) items.drop((maxItems - 1).coerceAtLeast(0)) else emptyList()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                visibleItems.forEach { item ->
                    ToolbarIconButton(item, onActionClick)
                }
                if (needsOverflow) {
                    OverflowMenuButton(overflowItems, onActionClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarIconButton(
    item: ResolvedQuickLaunchItem,
    onActionClick: (QuickLaunchAction) -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                if (item.description != null) {
                    Column {
                        Text(item.label, style = MaterialTheme.typography.labelMedium)
                        Text(item.description, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(item.label)
                }
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = { onActionClick(item.action) },
            enabled = item.enabled,
            modifier = Modifier
                .size(48.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.action.elementType?.color() ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun OverflowMenuButton(
    overflowItems: List<ResolvedQuickLaunchItem>,
    onActionClick: (QuickLaunchAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(app.aaps.core.ui.R.string.more_options),
                modifier = Modifier.size(24.dp)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            overflowItems.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.label)
                            if (item.description != null) {
                                Text(
                                    item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = item.action.elementType?.color() ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    enabled = item.enabled,
                    onClick = {
                        showMenu = false
                        onActionClick(item.action)
                    }
                )
            }
        }
    }
}

package app.aaps.core.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R

/**
 * Immutable UI model for a plugin in configuration/setup wizard screens.
 * All mutable PluginBase state is snapshotted at build time.
 */
@Immutable
data class ConfigPluginUiModel(
    val id: String,
    val name: String,
    val description: String?,
    val composeIcon: ImageVector?,
    val isEnabled: Boolean,
    val canToggle: Boolean,
    val showPreferences: Boolean
)

/**
 * Shared plugin item composable for configuration and setup wizard screens.
 * Displays a plugin with icon, name, description, toggle switch, and optional settings button.
 */
@Composable
fun ConfigPluginItem(
    plugin: ConfigPluginUiModel,
    onPluginClick: () -> Unit,
    onEnableToggle: (Boolean) -> Unit,
    onPreferencesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = MaterialTheme.colorScheme.primary
    val disabledAlpha = 0.38f

    val iconPainter = rememberVectorPainter(if (plugin.composeIcon != null) plugin.composeIcon else Icons.Filled.Settings)

    val containerColor = if (plugin.isEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = plugin.isEnabled, onClick = onPluginClick)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = plugin.description?.let { desc ->
                {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (plugin.isEnabled) iconColor.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = if (plugin.isEnabled) iconColor
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (plugin.showPreferences) {
                        IconButton(onClick = onPreferencesClick) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Switch(
                        checked = plugin.isEnabled,
                        onCheckedChange = if (plugin.canToggle) {
                            { onEnableToggle(it) }
                        } else null,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = containerColor)
        )
        if (plugin.isEnabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp)
            )
        }
    }
}

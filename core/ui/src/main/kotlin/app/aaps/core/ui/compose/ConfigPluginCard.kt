package app.aaps.core.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R

/**
 * How a plugin category lets the user choose plugins.
 * Drives the trailing selector (radio vs checkbox) and the tap behavior.
 */
enum class SelectionMode {

    SINGLE_SELECT, MULTI_SELECT
}

/**
 * Immutable UI model for a plugin in configuration / setup wizard screens.
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
    val showPreferences: Boolean,
    val hasContent: Boolean
)

/**
 * Card-based plugin item used in the Configuration sub-page and Setup Wizard.
 *
 * Tap target: the whole card.
 *   - Unselected card → fires [onCardClick] (caller toggles state).
 *   - Selected card → no-op for single-select; deselects for multi-select.
 *   - Always-on plugins (canToggle = false) → card is non-interactive.
 *
 * Inner buttons appear only when the card is selected and the corresponding
 * flag is enabled, so they never compete with the primary selection action.
 */
@Composable
fun ConfigPluginCard(
    plugin: ConfigPluginUiModel,
    selectionMode: SelectionMode,
    onCardClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenPluginClick: () -> Unit,
    modifier: Modifier = Modifier,
    editingEnabled: Boolean = true
) {
    val selected = plugin.isEnabled
    val canTap = plugin.canToggle && editingEnabled && when (selectionMode) {
        SelectionMode.SINGLE_SELECT -> !selected
        SelectionMode.MULTI_SELECT  -> true
    }

    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    }
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        onClick = onCardClick,
        enabled = canTap,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small),
        colors = colors,
        border = border,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            Row(verticalAlignment = Alignment.Top) {
                PluginIcon(plugin = plugin, selected = selected)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AapsSpacing.large)
                ) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    plugin.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AapsSpacing.small)
                        )
                    }
                }
                Selector(
                    selectionMode = selectionMode,
                    selected = selected,
                    enabled = plugin.canToggle && editingEnabled
                )
            }

            val showSettings = selected && plugin.showPreferences
            val showOpen = selected && plugin.hasContent
            if (showSettings || showOpen) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = AapsSpacing.large),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                if (showSettings) {
                    InCardActionRow(
                        leadingIcon = Icons.Filled.Settings,
                        label = stringResource(R.string.settings),
                        onClick = onSettingsClick
                    )
                }
                if (showOpen) {
                    if (showSettings) {
                        Spacer(modifier = Modifier.height(AapsSpacing.small))
                    }
                    InCardActionRow(
                        leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = stringResource(R.string.open_plugin),
                        onClick = onOpenPluginClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginIcon(plugin: ConfigPluginUiModel, selected: Boolean) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val iconPainter = rememberVectorPainter(
        if (plugin.composeIcon != null) plugin.composeIcon else Icons.Filled.Settings
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun Selector(
    selectionMode: SelectionMode,
    selected: Boolean,
    enabled: Boolean
) {
    when (selectionMode) {
        SelectionMode.SINGLE_SELECT -> RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled
        )

        SelectionMode.MULTI_SELECT  -> Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
private fun InCardActionRow(
    leadingIcon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.large)
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

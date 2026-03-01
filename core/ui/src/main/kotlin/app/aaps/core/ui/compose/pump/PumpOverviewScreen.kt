package app.aaps.core.ui.compose.pump

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.statusLevelToColor

/**
 * Shared pump overview screen used by all pump plugins.
 *
 * @param state The pump overview UI state produced by the per-pump ViewModel.
 * @param modifier Modifier for the root layout.
 * @param customContent Optional composable slot for pump-specific content (e.g. ComboV2 RT display frame).
 */
@Composable
fun PumpOverviewScreen(
    state: PumpOverviewUiState,
    modifier: Modifier = Modifier,
    customContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status banner
        state.statusBanner?.let { banner ->
            StatusBannerRow(banner)
        }

        // Queue status
        state.queueStatus?.let { queue ->
            QueueStatusRow(queue)
        }

        // Info section — pass all rows so AnimatedVisibility can animate in/out
        if (state.infoRows.isNotEmpty()) {
            InfoSection(state.infoRows)
        }

        // Custom content slot (e.g. ComboV2 RT display frame)
        customContent?.invoke()

        // Primary action buttons
        val visiblePrimary = state.primaryActions.filter { it.visible }
        if (visiblePrimary.isNotEmpty()) {
            ActionButtonsGrid(visiblePrimary)
        }

        // Management action buttons
        val visibleManagement = state.managementActions.filter { it.visible }
        if (visibleManagement.isNotEmpty()) {
            ActionButtonsGrid(visibleManagement)
        }
    }
}

@Composable
private fun StatusBannerRow(banner: StatusBanner) {
    val backgroundColor = when (banner.level) {
        StatusLevel.CRITICAL    -> MaterialTheme.colorScheme.errorContainer
        StatusLevel.WARNING     -> MaterialTheme.colorScheme.tertiaryContainer
        StatusLevel.NORMAL      -> MaterialTheme.colorScheme.primaryContainer
        StatusLevel.UNSPECIFIED -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = when (banner.level) {
        StatusLevel.CRITICAL    -> MaterialTheme.colorScheme.onErrorContainer
        StatusLevel.WARNING     -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusLevel.NORMAL      -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusLevel.UNSPECIFIED -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor
    ) {
        Text(
            text = banner.text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun QueueStatusRow(queue: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = queue,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoSection(rows: List<PumpInfoRow>) {
    AapsCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            rows.forEachIndexed { index, row ->
                AnimatedVisibility(visible = row.visible) {
                    Column {
                        PumpInfoRowItem(row)
                        if (index < rows.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PumpInfoRowItem(row: PumpInfoRow) {
    val valueColor = statusLevelToColor(row.level)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun ActionButtonsGrid(actions: List<PumpAction>) {
    actions.chunked(2).forEach { rowActions ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowActions.forEach { action ->
                TileButton(
                    text = action.label,
                    iconRes = action.iconRes,
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.weight(1f)
                )
            }
            if (rowActions.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TileButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val containerColor = if (enabled)
        MaterialTheme.colorScheme.surfaceContainerHigh
    else
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    val contentColor = if (enabled)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val iconColor = if (enabled)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(96.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

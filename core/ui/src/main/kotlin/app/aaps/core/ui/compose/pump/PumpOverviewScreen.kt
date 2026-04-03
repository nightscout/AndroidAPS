package app.aaps.core.ui.compose.pump

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.statusLevelToColor

/**
 * Shared pump overview screen used by all pump plugins.
 *
 * Layout order:
 * 1. Status banner (connection/pump state)
 * 2. Queue status (if present)
 * 3. Info rows card (label:value pairs)
 * 4. Custom content slot (pump image, RT display, etc.)
 * 5. Primary action buttons (Refresh, Reset Alarms, etc.)
 * 6. Management action buttons (Change Patch, Pair, etc.)
 *
 * @param state The pump overview UI state produced by the per-pump ViewModel.
 * @param modifier Modifier for the root layout.
 * @param customContent Optional composable slot for pump-specific content.
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Status banner + queue status (combined card)
            CommunicationStatusCard(state.statusBanner, state.queueStatus)

            // 3. Info rows
            if (state.infoRows.isNotEmpty()) {
                InfoSection(state.infoRows)
            }

            // 4. Custom content (pump image, etc.)
            customContent?.invoke()
        }

        // 5+6. Action buttons pinned to bottom
        ActionButtons(state.primaryActions + state.managementActions)
    }
}

// ── Communication status card (banner + queue in one card) ────────────────

@Composable
private fun CommunicationStatusCard(banner: StatusBanner?, queueStatus: String?) {
    if (banner == null && queueStatus == null) return

    val (bgColor, fgColor) = when (banner?.level) {
        StatusLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusLevel.WARNING  -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusLevel.NORMAL   -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusLevel.UNSPECIFIED,
        null                 -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = bgColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            banner?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = fgColor
                )
            }
            queueStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = fgColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Info section ───────────────────────────────────────────────────────────

@Composable
private fun InfoSection(rows: List<PumpInfoRow>) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            rows.forEachIndexed { index, row ->
                AnimatedVisibility(visible = row.visible) {
                    Column {
                        InfoRowItem(row)
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
private fun InfoRowItem(row: PumpInfoRow) {
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
            color = statusLevelToColor(row.level),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

// ── Action buttons ─────────────────────────────────────────────────────────

@Composable
private fun ActionButtons(actions: List<PumpAction>) {
    val visible = actions.filter { it.visible }
    if (visible.isEmpty()) return

    visible.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row.forEach { action ->
                FilledTonalButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (action.icon != null) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Unspecified
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = action.iconRes),
                            contentDescription = action.label,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(text = action.label, maxLines = 1)
                }
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

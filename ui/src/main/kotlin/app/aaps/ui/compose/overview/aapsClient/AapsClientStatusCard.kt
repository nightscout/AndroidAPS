package app.aaps.ui.compose.overview.aapsClient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.AapsClientLevel
import app.aaps.core.interfaces.overview.graph.AapsClientStatusData
import app.aaps.core.interfaces.overview.graph.AapsClientStatusItem
import app.aaps.core.ui.R

@Composable
fun AapsClientStatusCard(
    statusData: AapsClientStatusData,
    flavorTint: Color,
    modifier: Modifier = Modifier
) {
    val items = listOfNotNull(statusData.pump, statusData.openAps, statusData.uploader)
    if (items.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Header row — compact chips + expand/collapse toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(flavorTint),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items.forEach { item ->
                        AapsClientStatusChip(item = item)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse else R.string.expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }

            // Expanded: detail rows
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        AapsClientDetailRow(item = item)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AapsClientStatusCardCollapsedPreview() {
    MaterialTheme {
        AapsClientStatusCard(
            statusData = AapsClientStatusData(
                pump = AapsClientStatusItem(
                    label = "Pump",
                    value = "2 min ago",
                    level = AapsClientLevel.INFO,
                    dialogTitle = "Pump status",
                    dialogText = "Last connection: 2 min ago\nReservoir: 120 U"
                ),
                openAps = AapsClientStatusItem(
                    label = "OpenAPS",
                    value = "1 min ago",
                    level = AapsClientLevel.INFO,
                    dialogTitle = "OpenAPS",
                    dialogText = "Last enacted: 1 min ago"
                ),
                uploader = AapsClientStatusItem(
                    label = "Uploader",
                    value = "85%",
                    level = AapsClientLevel.INFO,
                    dialogTitle = "Uploader",
                    dialogText = "Battery: 85%"
                )
            ),
            flavorTint = Color(0x40FF9800)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AapsClientStatusCardMixedLevelsPreview() {
    MaterialTheme {
        AapsClientStatusCard(
            statusData = AapsClientStatusData(
                pump = AapsClientStatusItem(
                    label = "Pump",
                    value = "12 min ago",
                    level = AapsClientLevel.WARN,
                    dialogTitle = "Pump status",
                    dialogText = "Last connection: 12 min ago\nReservoir: 45 U"
                ),
                openAps = AapsClientStatusItem(
                    label = "OpenAPS",
                    value = "16 min ago",
                    level = AapsClientLevel.URGENT,
                    dialogTitle = "OpenAPS",
                    dialogText = "Last enacted: 16 min ago"
                )
            ),
            flavorTint = Color(0x40FF9800)
        )
    }
}

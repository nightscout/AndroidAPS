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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import app.aaps.core.ui.CoreUiStrings
import app.aaps.ui.UiStrings
import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.AapsClientStatusData
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.rememberBringIntoViewOnExpand

/**
 * @see AapsClientStatusCardCollapsedPreview
 * @see AapsClientStatusCardMixedLevelsPreview
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AapsClientStatusCard(
    statusData: AapsClientStatusData,
    flavorTint: Color,
    modifier: Modifier = Modifier
) {
    val items = listOfNotNull(statusData.pump, statusData.openAps, statusData.uploader)
    if (items.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandRequester = rememberBringIntoViewOnExpand(expanded)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .bringIntoViewRequester(expandRequester),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            // Header row — compact chips + expand/collapse toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(flavorTint)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    items.forEach { item ->
                        AapsClientStatusChip(item = item)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) CoreUiStrings.collapse else CoreUiStrings.expand
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
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
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

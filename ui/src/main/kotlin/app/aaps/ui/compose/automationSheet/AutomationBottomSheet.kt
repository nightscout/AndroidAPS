package app.aaps.ui.compose.automationSheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.TonalIcon
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationBottomSheet(
    onDismiss: () -> Unit,
    automationItems: List<AutomationActionItem>,
    onItemClick: (AutomationActionItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .consumeOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(CoreUiR.string.automation),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            val automationColor = ElementType.AUTOMATION.color()
            automationItems.forEach { item ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = item.title,
                            color = automationColor
                        )
                    },
                    supportingContent = if (item.triggerIconResIds.isNotEmpty() || item.actionIconResIds.isNotEmpty()) {
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                item.triggerIconResIds.forEach { resId ->
                                    Icon(
                                        painter = painterResource(resId),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.triggerIconResIds.isNotEmpty() && item.actionIconResIds.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .padding(horizontal = 2.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                item.actionIconResIds.forEach { resId ->
                                    Icon(
                                        painter = painterResource(resId),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else null,
                    leadingContent = {
                        val iconResId = item.iconResId
                        if (iconResId != null) {
                            TonalIcon(
                                painter = painterResource(iconResId),
                                color = automationColor
                            )
                        } else {
                            TonalIcon(
                                painter = rememberVectorPainter(IcAutomation),
                                color = automationColor
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable {
                        onDismiss()
                        onItemClick(item)
                    }
                )
            }
        }
    }
}

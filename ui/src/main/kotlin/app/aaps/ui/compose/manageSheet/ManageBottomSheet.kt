package app.aaps.ui.compose.manageSheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.actions.CustomAction
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.icons.IcCancelExtendedBolus
import app.aaps.core.ui.compose.icons.IcTbrCancel
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.descriptionResId
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBottomSheet(
    onDismiss: () -> Unit,
    isSimpleMode: Boolean,
    // Visibility flags
    showTempTarget: Boolean,
    showTempBasal: Boolean,
    showCancelTempBasal: Boolean,
    showExtendedBolus: Boolean,
    showCancelExtendedBolus: Boolean,
    // Cancel text strings
    cancelTempBasalText: String,
    cancelExtendedBolusText: String,
    // Pump
    isPatchPump: Boolean,
    pumpPlugin: PluginBase,
    customActions: List<CustomAction>,
    // Callbacks
    onNavigate: (NavigationRequest) -> Unit,
    onCancelTempBasalClick: () -> Unit,
    onCancelExtendedBolusClick: () -> Unit,
    onCustomActionClick: (CustomAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        ManageBottomSheetContent(
            isSimpleMode = isSimpleMode,
            showTempTarget = showTempTarget,
            showTempBasal = showTempBasal,
            showCancelTempBasal = showCancelTempBasal,
            showExtendedBolus = showExtendedBolus,
            showCancelExtendedBolus = showCancelExtendedBolus,
            cancelTempBasalText = cancelTempBasalText,
            cancelExtendedBolusText = cancelExtendedBolusText,
            isPatchPump = isPatchPump,
            pumpPlugin = pumpPlugin,
            customActions = customActions,
            onDismiss = onDismiss,
            onNavigate = onNavigate,
            onCancelTempBasalClick = onCancelTempBasalClick,
            onCancelExtendedBolusClick = onCancelExtendedBolusClick,
            onCustomActionClick = onCustomActionClick
        )
    }
}

@Composable
internal fun ManageBottomSheetContent(
    isSimpleMode: Boolean = false,
    showTempTarget: Boolean,
    showTempBasal: Boolean,
    showCancelTempBasal: Boolean,
    showExtendedBolus: Boolean,
    showCancelExtendedBolus: Boolean,
    cancelTempBasalText: String,
    cancelExtendedBolusText: String,
    isPatchPump: Boolean = false,
    pumpPlugin: PluginBase? = null,
    customActions: List<CustomAction>,
    onDismiss: () -> Unit = {},
    onNavigate: (NavigationRequest) -> Unit = {},
    onCancelTempBasalClick: () -> Unit = {},
    onCancelExtendedBolusClick: () -> Unit = {},
    onCustomActionClick: (CustomAction) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Section: Manage
        SectionHeader(stringResource(CoreUiR.string.manage))

        GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
            add { modifier ->
                ManageGridItem(
                    elementType = ElementType.PROFILE_MANAGEMENT,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                    modifier = modifier
                )
            }
            add { modifier ->
                ManageGridItem(
                    elementType = ElementType.INSULIN_MANAGEMENT,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                    modifier = modifier
                )
            }
            if (showTempTarget) {
                add { modifier ->
                    ManageGridItem(
                        elementType = ElementType.TEMP_TARGET_MANAGEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
            }
            add { modifier ->
                ManageGridItem(
                    elementType = ElementType.QUICK_WIZARD_MANAGEMENT,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                    modifier = modifier
                )
            }
            add { modifier ->
                ManageGridItem(
                    elementType = ElementType.FOOD_MANAGEMENT,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                    modifier = modifier
                )
            }
            add { modifier ->
                ManageGridItem(
                    elementType = ElementType.SITE_ROTATION,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate,
                    modifier = modifier
                )
            }
            if (pumpPlugin != null) {
                add { modifier ->
                    @Suppress("DEPRECATION")
                    ManageGridItem(
                        text = stringResource(CoreUiR.string.pump_management),
                        iconPainter = pumpPlugin.pluginDescription.icon?.let { rememberVectorPainter(it) }
                            ?: if (pumpPlugin.menuIcon != -1) painterResource(pumpPlugin.menuIcon)
                            else rememberVectorPainter(ElementType.PUMP.icon()),
                        color = ElementType.PUMP.color(),
                        onDismiss = onDismiss,
                        onClick = { onNavigate(NavigationRequest.Element(ElementType.PUMP)) },
                        description = pumpPlugin.name,
                        modifier = modifier
                    )
                }
            }
        }

        // Section: Device maintenance & basal
        if (showTempBasal || showCancelTempBasal || showExtendedBolus || showCancelExtendedBolus) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            Spacer(modifier = Modifier.height(8.dp))

            GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                add { modifier ->
                    ManageGridItem(
                        elementType = ElementType.SENSOR_INSERT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        modifier = modifier
                    )
                }
                if (!isPatchPump) {
                    add { modifier ->
                        ManageGridItem(
                            elementType = ElementType.FILL,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            modifier = modifier
                        )
                    }
                }
                if (!isSimpleMode) {
                    if (showCancelTempBasal) {
                        add { modifier ->
                            ManageGridItem(
                                text = cancelTempBasalText,
                                iconPainter = rememberVectorPainter(IcTbrCancel),
                                color = ElementType.TEMP_BASAL.color(),
                                onDismiss = onDismiss,
                                onClick = onCancelTempBasalClick,
                                modifier = modifier
                            )
                        }
                    } else if (showTempBasal) {
                        add { modifier ->
                            ManageGridItem(
                                elementType = ElementType.TEMP_BASAL,
                                text = stringResource(CoreUiR.string.tempbasal_button),
                                onDismiss = onDismiss,
                                onNavigate = onNavigate,
                                modifier = modifier
                            )
                        }
                    }
                    if (showCancelExtendedBolus) {
                        add { modifier ->
                            ManageGridItem(
                                text = cancelExtendedBolusText,
                                iconPainter = rememberVectorPainter(IcCancelExtendedBolus),
                                color = ElementType.EXTENDED_BOLUS.color(),
                                onDismiss = onDismiss,
                                onClick = onCancelExtendedBolusClick,
                                modifier = modifier
                            )
                        }
                    } else if (showExtendedBolus) {
                        add { modifier ->
                            ManageGridItem(
                                elementType = ElementType.EXTENDED_BOLUS,
                                text = stringResource(CoreUiR.string.extended_bolus_button),
                                onDismiss = onDismiss,
                                onNavigate = onNavigate,
                                modifier = modifier
                            )
                        }
                    }
                }
            }
        }

        // Section: Careportal (hidden in simple mode, collapsed by default)
        if (!isSimpleMode) {
            var careportalExpanded by remember { mutableStateOf(false) }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            CollapsibleSectionHeader(
                text = stringResource(CoreUiR.string.careportal),
                expanded = careportalExpanded,
                onToggle = { careportalExpanded = !careportalExpanded }
            )

            AnimatedVisibility(visible = careportalExpanded) {
                GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                    add { modifier ->
                        ManageGridItem(
                            elementType = ElementType.BG_CHECK,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add { modifier ->
                        ManageGridItem(
                            elementType = ElementType.NOTE,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add { modifier ->
                        ManageGridItem(
                            elementType = ElementType.EXERCISE,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add { modifier ->
                        ManageGridItem(
                            elementType = ElementType.QUESTION,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                    add { modifier ->
                        ManageGridItem(
                            elementType = ElementType.ANNOUNCEMENT,
                            onDismiss = onDismiss,
                            onNavigate = onNavigate,
                            coloredText = false,
                            modifier = modifier
                        )
                    }
                }
            }
        }

        // Section: Pump actions (only if non-empty)
        if (customActions.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
            SectionHeader(stringResource(CoreUiR.string.pump_actions))

            val pumpColor = ElementType.PUMP.color()
            GridSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                customActions.forEach { action ->
                    add { modifier ->
                        ManageGridItem(
                            text = stringResource(action.name),
                            iconPainter = painterResource(action.iconResourceId),
                            color = pumpColor,
                            onDismiss = onDismiss,
                            onClick = { onCustomActionClick(action) },
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lays out composable items in a 2-column grid with equal-height rows.
 */
@Composable
private fun GridSection(
    modifier: Modifier = Modifier,
    content: GridSectionScope.() -> Unit
) {
    val items = GridSectionScope().apply(content).items
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                rowItems.forEach { item ->
                    item(Modifier.weight(1f).fillMaxHeight())
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private class GridSectionScope {
    val items = mutableListOf<@Composable (Modifier) -> Unit>()
    fun add(item: @Composable (Modifier) -> Unit) {
        items.add(item)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun CollapsibleSectionHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Grid card item that derives icon, color, label, and description from [ElementType].
 * Custom [text] overrides the ElementType label when provided.
 */
@Composable
private fun ManageGridItem(
    elementType: ElementType,
    onDismiss: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    coloredText: Boolean = true
) {
    val color = elementType.color()
    val label = text ?: stringResource(elementType.labelResId())
    val descResId = elementType.descriptionResId()
    val description = if (descResId != 0) stringResource(descResId) else null
    ManageGridItem(
        text = label,
        iconPainter = rememberVectorPainter(elementType.icon()),
        color = color,
        onDismiss = onDismiss,
        onClick = { onNavigate(NavigationRequest.Element(elementType)) },
        description = description,
        coloredText = coloredText,
        modifier = modifier
    )
}

@Composable
private fun ManageGridItem(
    text: String,
    iconPainter: Painter,
    color: Color,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    coloredText: Boolean = true
) {
    ElevatedCard(
        onClick = {
            onDismiss()
            onClick()
        },
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallTonalIcon(painter = iconPainter, color = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (coloredText) color else Color.Unspecified
                )
            }
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmallTonalIcon(painter: Painter, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .background(color = color.copy(alpha = 0.12f), shape = CircleShape)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManageBottomSheetContentPreview() {
    MaterialTheme {
        ManageBottomSheetContent(
            showTempTarget = true,
            showTempBasal = true,
            showCancelTempBasal = false,
            showExtendedBolus = true,
            showCancelExtendedBolus = false,
            cancelTempBasalText = "",
            cancelExtendedBolusText = "",
            customActions = emptyList()
        )
    }
}

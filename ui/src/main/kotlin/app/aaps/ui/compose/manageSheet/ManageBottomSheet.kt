package app.aaps.ui.compose.manageSheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import app.aaps.core.ui.compose.TonalIcon
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

        // Profile Management
        ManageItem(
            elementType = ElementType.PROFILE_MANAGEMENT,
            onDismiss = onDismiss,
            onNavigate = onNavigate
        )

        // Insulin Management
        ManageItem(
            elementType = ElementType.INSULIN_MANAGEMENT,
            onDismiss = onDismiss,
            onNavigate = onNavigate
        )

        // Temp Target
        if (showTempTarget) {
            ManageItem(
                elementType = ElementType.TEMP_TARGET_MANAGEMENT,
                onDismiss = onDismiss,
                onNavigate = onNavigate
            )
        }

        ManageItem(
            elementType = ElementType.QUICK_WIZARD_MANAGEMENT,
            onDismiss = onDismiss,
            onNavigate = onNavigate
        )

        ManageItem(
            elementType = ElementType.SITE_ROTATION,
            onDismiss = onDismiss,
            onNavigate = onNavigate
        )

        if (pumpPlugin != null) {
            @Suppress("DEPRECATION")
            ManageItem(
                text = stringResource(CoreUiR.string.pump_management),
                iconPainter = pumpPlugin.pluginDescription.icon?.let { rememberVectorPainter(it) }
                    ?: if (pumpPlugin.menuIcon != -1) painterResource(pumpPlugin.menuIcon)
                    else rememberVectorPainter(ElementType.PUMP.icon()),
                color = ElementType.PUMP.color(),
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.PUMP)) },
                description = pumpPlugin.name
            )
        }

        // Section: Basal (only if any basal item is visible)
        if (showTempBasal || showCancelTempBasal || showExtendedBolus || showCancelExtendedBolus) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // Sensor Insert & Fill
            ManageItem(
                elementType = ElementType.SENSOR_INSERT,
                onDismiss = onDismiss,
                onNavigate = onNavigate
            )
            if (!isPatchPump) {
                ManageItem(
                    elementType = ElementType.FILL,
                    onDismiss = onDismiss,
                    onNavigate = onNavigate
                )
            }

            // Temp Basal or Cancel Temp Basal
            if (!isSimpleMode) {
                if (showCancelTempBasal) {
                    val cancelColor = ElementType.TEMP_BASAL.color()
                    ManageItem(
                        text = cancelTempBasalText,
                        iconPainter = rememberVectorPainter(IcTbrCancel),
                        color = cancelColor,
                        onDismiss = onDismiss,
                        onClick = onCancelTempBasalClick
                    )
                } else if (showTempBasal) {
                    ManageItem(
                        elementType = ElementType.TEMP_BASAL,
                        text = stringResource(CoreUiR.string.tempbasal_button),
                        onDismiss = onDismiss,
                        onNavigate = onNavigate
                    )
                }

                // Extended Bolus or Cancel Extended Bolus
                if (showCancelExtendedBolus) {
                    val cancelColor = ElementType.EXTENDED_BOLUS.color()
                    ManageItem(
                        text = cancelExtendedBolusText,
                        iconPainter = rememberVectorPainter(IcCancelExtendedBolus),
                        color = cancelColor,
                        onDismiss = onDismiss,
                        onClick = onCancelExtendedBolusClick
                    )
                } else if (showExtendedBolus) {
                    ManageItem(
                        elementType = ElementType.EXTENDED_BOLUS,
                        text = stringResource(CoreUiR.string.extended_bolus_button),
                        onDismiss = onDismiss,
                        onNavigate = onNavigate
                    )
                }
            }
        }

        // Section: Careportal (hidden in simple mode, collapsed by default)
        if (!isSimpleMode) {
            var careportalExpanded by remember { mutableStateOf(false) }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            CollapsibleSectionHeader(
                text = stringResource(CoreUiR.string.careportal),
                expanded = careportalExpanded,
                onToggle = { careportalExpanded = !careportalExpanded }
            )

            AnimatedVisibility(visible = careportalExpanded) {
                Column {
                    ManageItem(
                        elementType = ElementType.BG_CHECK,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        coloredText = false
                    )
                    ManageItem(
                        elementType = ElementType.NOTE,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        coloredText = false
                    )
                    ManageItem(
                        elementType = ElementType.EXERCISE,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        coloredText = false
                    )
                    ManageItem(
                        elementType = ElementType.QUESTION,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        coloredText = false
                    )
                    ManageItem(
                        elementType = ElementType.ANNOUNCEMENT,
                        onDismiss = onDismiss,
                        onNavigate = onNavigate,
                        coloredText = false
                    )
                }
            }
        }

        // Section: Pump actions (only if non-empty)
        if (customActions.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SectionHeader(stringResource(CoreUiR.string.pump_actions))

            val pumpColor = ElementType.PUMP.color()
            customActions.forEach { action ->
                ManageItem(
                    text = stringResource(action.name),
                    iconPainter = painterResource(action.iconResourceId),
                    color = pumpColor,
                    onDismiss = onDismiss,
                    onClick = { onCustomActionClick(action) }
                )
            }
        }
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
 * ManageItem that derives icon, color, label, and description from [ElementType].
 * Fires [onNavigate] with a [NavigationRequest.Element] — caller handles protection + navigation.
 * Custom [text] overrides the ElementType label when provided.
 */
@Composable
private fun ManageItem(
    elementType: ElementType,
    onDismiss: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    text: String? = null,
    coloredText: Boolean = true
) {
    val color = elementType.color()
    val label = text ?: stringResource(elementType.labelResId())
    val descResId = elementType.descriptionResId()
    val description = if (descResId != 0) stringResource(descResId) else null
    ManageItem(
        text = label,
        iconPainter = rememberVectorPainter(elementType.icon()),
        color = color,
        onDismiss = onDismiss,
        onClick = { onNavigate(NavigationRequest.Element(elementType)) },
        description = description,
        coloredText = coloredText
    )
}

@Composable
private fun ManageItem(
    text: String,
    iconPainter: Painter,
    color: Color,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    description: String? = null,
    coloredText: Boolean = true
) {
    ListItem(
        headlineContent = {
            Text(text = text, color = if (coloredText) color else Color.Unspecified)
        },
        supportingContent = description?.let {
            { Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = {
            TonalIcon(painter = iconPainter, color = color)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable {
            onDismiss()
            onClick()
        }
    )
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

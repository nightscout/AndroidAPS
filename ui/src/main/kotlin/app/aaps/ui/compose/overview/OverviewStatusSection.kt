package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.AdaptivePreferenceList
import app.aaps.core.ui.compose.preference.PreferenceCategory
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.ui.compose.overview.statusLights.StatusItem
import app.aaps.ui.compose.overview.statusLights.StatusSectionContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewStatusSection(
    sensorStatus: StatusItem?,
    insulinStatus: StatusItem?,
    cannulaStatus: StatusItem?,
    batteryStatus: StatusItem?,
    showFill: Boolean,
    showPumpBatteryChange: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    statusLightsDef: PreferenceSubScreenDef,
    onCopyFromNightscout: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOfNotNull(cannulaStatus, insulinStatus, sensorStatus, batteryStatus)
    if (items.isEmpty()) return
    val compactItems = items.filter { it.compactAge || (it.compactLevel && it.level != null) }

    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expanded) {
                    Text(
                        text = stringResource(app.aaps.core.ui.R.string.status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onExpandedChange(false) }
                    )
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ExpandLess,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.collapse),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onExpandedChange(false) }
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onExpandedChange(true) },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        compactItems.forEach { item ->
                            CompactStatusItem(item = item)
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onExpandedChange(true) }
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusSectionContent(
                        sensorStatus = sensorStatus,
                        insulinStatus = insulinStatus,
                        cannulaStatus = cannulaStatus,
                        batteryStatus = batteryStatus,
                        onSensorInsertClick = { onNavigate(NavigationRequest.Element(ElementType.SENSOR_INSERT)) },
                        onFillClick = if (showFill) {
                            { onNavigate(NavigationRequest.Element(ElementType.CANNULA_CHANGE)) }
                        } else null,
                        onInsulinChangeClick = if (showFill) {
                            { onNavigate(NavigationRequest.Element(ElementType.FILL)) }
                        } else null,
                        onBatteryChangeClick = if (showPumpBatteryChange) {
                            { onNavigate(NavigationRequest.Element(ElementType.BATTERY_CHANGE)) }
                        } else null
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        StatusLightsSettingsBottomSheet(
            settingsDef = statusLightsDef,
            onDismiss = { showSettingsSheet = false },
            onCopyFromNightscout = onCopyFromNightscout,
            sheetState = sheetState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusLightsSettingsBottomSheet(
    settingsDef: PreferenceSubScreenDef,
    onDismiss: () -> Unit,
    onCopyFromNightscout: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        StatusLightsSettingsContent(
            settingsDef = settingsDef,
            onCopyFromNightscout = onCopyFromNightscout
        )
    }
}

@Composable
private fun StatusLightsSettingsContent(
    settingsDef: PreferenceSubScreenDef,
    onCopyFromNightscout: () -> Unit
) {
    var showCopyDialog by remember { mutableStateOf(false) }

    val groups = listOf(
        stringResource(app.aaps.core.ui.R.string.cannula) to listOf(IntKey.OverviewCageWarning, IntKey.OverviewCageCritical),
        stringResource(app.aaps.core.ui.R.string.insulin_label) to listOf(IntKey.OverviewIageWarning, IntKey.OverviewIageCritical, IntKey.OverviewResWarning, IntKey.OverviewResCritical),
        stringResource(app.aaps.core.ui.R.string.sensor_label) to listOf(IntKey.OverviewSageWarning, IntKey.OverviewSageCritical, IntKey.OverviewSbatWarning, IntKey.OverviewSbatCritical),
        stringResource(app.aaps.core.ui.R.string.pb_label) to listOf(IntKey.OverviewBageWarning, IntKey.OverviewBageCritical, IntKey.OverviewBattWarning, IntKey.OverviewBattCritical)
    )

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(app.aaps.core.ui.R.string.statuslights),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        ProvidePreferenceTheme {
            groups.forEach { (categoryTitle, keys) ->
                PreferenceCategory(title = { Text(categoryTitle) })
                AdaptivePreferenceList(items = keys)
            }
        }

        FilledTonalButton(
            onClick = { showCopyDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = stringResource(app.aaps.core.ui.R.string.copy_existing_values))
        }
    }

    if (showCopyDialog) {
        OkCancelDialog(
            title = stringResource(app.aaps.core.ui.R.string.statuslights),
            message = stringResource(app.aaps.core.ui.R.string.copy_existing_values),
            onConfirm = {
                onCopyFromNightscout()
                showCopyDialog = false
            },
            onDismiss = { showCopyDialog = false }
        )
    }
}

@Composable
private fun CompactStatusItem(item: StatusItem) {
    val showAge = item.compactAge
    val showLevel = item.compactLevel && item.level != null
    if (!showAge && !showLevel) return

    val ageColor = statusLevelToColor(item.ageStatus)
    val levelColor = if (item.level != null) statusLevelToColor(item.levelStatus) else ageColor

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = buildAnnotatedString {
                if (showAge) {
                    withStyle(SpanStyle(color = ageColor)) {
                        append(item.age)
                    }
                }
                if (showLevel) {
                    withStyle(SpanStyle(color = levelColor)) {
                        if (showAge) append(" ")
                        append(item.level)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private val previewStatusItems = Triple(
    StatusItem(
        label = "Cannula",
        age = "16h",
        ageStatus = StatusLevel.NORMAL,
        level = null,
        icon = Icons.Default.Circle
    ),
    StatusItem(
        label = "Insulin",
        age = "16h",
        ageStatus = StatusLevel.NORMAL,
        level = "10 U",
        levelStatus = StatusLevel.NORMAL,
        icon = Icons.Default.Circle
    ),
    StatusItem(
        label = "Sensor",
        age = "3d",
        ageStatus = StatusLevel.WARNING,
        level = "82%",
        levelStatus = StatusLevel.NORMAL,
        icon = Icons.Default.Circle
    )
)

private val previewBatteryItem = StatusItem(
    label = "Battery",
    age = "2d",
    ageStatus = StatusLevel.NORMAL,
    level = "68%",
    levelStatus = StatusLevel.NORMAL,
    icon = Icons.Default.Circle
)

private val previewStatusLightsDef = PreferenceSubScreenDef(
    key = "preview",
    titleResId = 0
)

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun OverviewStatusSectionCollapsedPreview() {
    MaterialTheme {
        val (cannula, insulin, sensor) = previewStatusItems
        OverviewStatusSection(
            sensorStatus = sensor,
            insulinStatus = insulin,
            cannulaStatus = cannula,
            batteryStatus = previewBatteryItem,
            showFill = true,
            showPumpBatteryChange = true,
            onNavigate = {},
            statusLightsDef = previewStatusLightsDef,
            onCopyFromNightscout = {},
            expanded = false,
            onExpandedChange = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun OverviewStatusSectionExpandedPreview() {
    MaterialTheme {
        val (cannula, insulin, sensor) = previewStatusItems
        OverviewStatusSection(
            sensorStatus = sensor,
            insulinStatus = insulin,
            cannulaStatus = cannula,
            batteryStatus = previewBatteryItem,
            showFill = true,
            showPumpBatteryChange = true,
            onNavigate = {},
            statusLightsDef = previewStatusLightsDef,
            onCopyFromNightscout = {},
            expanded = true,
            onExpandedChange = {}
        )
    }
}

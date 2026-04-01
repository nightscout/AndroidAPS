package app.aaps.ui.compose.overview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcSettingsOff
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.AdaptivePreferenceList
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.notificationsSheet.NotificationBottomSheet
import app.aaps.ui.compose.notificationsSheet.NotificationFab
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.IobCobChipsRow
import app.aaps.ui.compose.overview.chips.ProfileChip
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.SensitivityChip
import app.aaps.ui.compose.overview.chips.TempTargetChip
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusItem
import app.aaps.ui.compose.overview.statusLights.StatusSectionContent
import app.aaps.ui.compose.overview.statusLights.StatusViewModel

@Composable
fun OverviewScreen(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeProgress: Float,
    isSimpleMode: Boolean,
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    paddingValues: PaddingValues,
    fabBottomOffset: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    LocalDateUtil.current
    // Collect BG info state from ViewModel
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

    // Notification bottom sheet state
    var showNotificationSheet by remember { mutableStateOf(false) }

    // Auto-show bottom sheet on resume when urgent notifications exist
    LaunchedEffect(autoShowNotificationSheet) {
        if (autoShowNotificationSheet) {
            showNotificationSheet = true
            onAutoShowConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Calculation progress bar
            if (calcProgress < 100) {
                LinearProgressIndicator(
                    progress = { calcProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
            // BG Info and Chips in a row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // BG Info section + sensitivity chip on the left
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BgInfoSection(
                        bgInfo = bgInfoState.bgInfo,
                        timeAgoText = bgInfoState.timeAgoText
                    )
                    // Sensitivity / Autosens chip (hidden when ratio is 100% with no extra info)
                    val sensitivityUiState by graphViewModel.sensitivityUiState.collectAsStateWithLifecycle()
                    if (sensitivityUiState.asText.isNotEmpty() || sensitivityUiState.isfFrom.isNotEmpty()) {
                        var showSensitivityDialog by remember { mutableStateOf(false) }
                        SensitivityChip(
                            state = sensitivityUiState,
                            onClick = { if (sensitivityUiState.dialogText.isNotEmpty()) showSensitivityDialog = true }
                        )
                        if (showSensitivityDialog) {
                            OkCancelDialog(
                                title = stringResource(app.aaps.core.ui.R.string.sensitivity),
                                message = sensitivityUiState.dialogText,
                                onConfirm = { showSensitivityDialog = false },
                                onDismiss = { showSensitivityDialog = false }
                            )
                        }
                    }
                }

                // Chips column on the right
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Running mode chip + simple mode icon
                    if (runningModeText.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RunningModeChip(
                                mode = runningMode,
                                text = runningModeText,
                                progress = runningModeProgress,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }
                            )
                            if (isSimpleMode) {
                                Icon(
                                    imageVector = IcSettingsOff,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.simple_mode),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                    // Profile chip
                    if (profileName.isNotEmpty()) {
                        ProfileChip(
                            profileName = profileName,
                            isModified = isProfileModified,
                            progress = profileProgress,
                            onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) }
                        )
                    }
                    // TempTarget chip (show when text is available)
                    if (tempTargetText.isNotEmpty()) {
                        TempTargetChip(
                            targetText = tempTargetText,
                            state = tempTargetState,
                            progress = tempTargetProgress,
                            reason = tempTargetReason,
                            onClick = { onNavigate(NavigationRequest.Element(ElementType.TEMP_TARGET_MANAGEMENT)) }
                        )
                    }
                    // IOB + COB chips row
                    val iobUiState by graphViewModel.iobUiState.collectAsStateWithLifecycle()
                    val cobUiState by graphViewModel.cobUiState.collectAsStateWithLifecycle()
                    IobCobChipsRow(
                        iobUiState = iobUiState,
                        cobUiState = cobUiState
                    )
                }
            }

            // Status section with expand/collapse
            OverviewStatusSection(
                sensorStatus = statusState.sensorStatus,
                insulinStatus = statusState.insulinStatus,
                cannulaStatus = statusState.cannulaStatus,
                batteryStatus = statusState.batteryStatus,
                showFill = statusState.showFill,
                showPumpBatteryChange = statusState.showPumpBatteryChange,
                onNavigate = onNavigate,
                statusLightsDef = statusLightsDef,
                onCopyFromNightscout = { manageViewModel.copyStatusLightsFromNightscout() }
            )

            // NSClient status card (only in AAPSCLIENT builds)
            if (config.AAPSCLIENT) {
                val nsClientStatus by graphViewModel.nsClientStatusFlow.collectAsStateWithLifecycle()
                val flavorTint = when {
                    config.AAPSCLIENT3 -> AapsTheme.generalColors.flavorClient3Tint
                    config.AAPSCLIENT2 -> AapsTheme.generalColors.flavorClient2Tint
                    else               -> AapsTheme.generalColors.flavorClient1Tint
                }
                AapsClientStatusCard(
                    statusData = nsClientStatus,
                    flavorTint = flavorTint
                )
            }

            // Graph content - New Compose/Vico graphs
            GraphsSection(graphViewModel = graphViewModel)
        }

        // Notification FAB overlay
        NotificationFab(
            notificationCount = notifications.size,
            highestLevel = notifications.minByOrNull { it.level.ordinal }?.level,
            onClick = { showNotificationSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(paddingValues)
                .padding(end = 16.dp, bottom = 72.dp + fabBottomOffset)
        )
    }

    // Notification bottom sheet
    if (showNotificationSheet && notifications.isNotEmpty()) {
        NotificationBottomSheet(
            notifications = notifications,
            onDismissSheet = { showNotificationSheet = false },
            onDismissNotification = onDismissNotification,
            onNotificationActionClick = onNotificationActionClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewStatusSection(
    sensorStatus: StatusItem?,
    insulinStatus: StatusItem?,
    cannulaStatus: StatusItem?,
    batteryStatus: StatusItem?,
    showFill: Boolean,
    showPumpBatteryChange: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    statusLightsDef: PreferenceSubScreenDef,
    onCopyFromNightscout: () -> Unit
) {
    val items = listOfNotNull(cannulaStatus, insulinStatus, sensorStatus, batteryStatus)
    if (items.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ElevatedCard(
        modifier = Modifier
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
            // Header row — clickable to toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (expanded) {
                    // Status title with clickable area for collapse
                    Text(
                        text = stringResource(app.aaps.core.ui.R.string.status),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { expanded = false }
                    )
                    // Settings icon
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
                    // Collapse icon
                    Icon(
                        imageVector = Icons.Filled.ExpandLess,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.collapse),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expanded = false }
                    )
                } else {
                    // Collapsed: compact status items
                    FlowRow(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { expanded = true },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items.forEach { item ->
                            CompactStatusItem(item = item)
                        }
                    }
                    // Expand icon
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expanded = true }
                    )
                }
            }

            // Expanded: full status rows with action buttons
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

    // Settings bottom sheet
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
    sheetState: androidx.compose.material3.SheetState
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

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Header
        Text(
            text = stringResource(app.aaps.core.ui.R.string.statuslights),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )

        // Settings list
        ProvidePreferenceTheme {
            AdaptivePreferenceList(
                items = settingsDef.items
            )
        }

        // "Copy from Nightscout" button
        FilledTonalButton(
            onClick = { showCopyDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = stringResource(app.aaps.core.ui.R.string.copy_existing_values))
        }
    }

    // Confirmation dialog
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
                withStyle(SpanStyle(color = ageColor)) {
                    append(item.age)
                }
                if (item.level != null) {
                    withStyle(SpanStyle(color = levelColor)) {
                        append(" ")
                        append(item.level)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


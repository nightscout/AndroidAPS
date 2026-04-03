package app.aaps.ui.compose.carbsDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.CarbTimeRow
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.core.ui.compose.preference.AdaptivePreferenceList
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.ui.compose.EventDatePicker
import app.aaps.ui.compose.EventTimePicker
import app.aaps.ui.compose.components.DialogStatusBar
import app.aaps.ui.compose.overview.graphs.BgInfoUiState
import app.aaps.ui.compose.overview.graphs.CobUiState
import app.aaps.ui.compose.overview.graphs.IobUiState
import kotlinx.coroutines.flow.StateFlow
import java.text.DecimalFormat
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@Composable
fun CarbsDialogScreen(
    viewModel: CarbsDialogViewModel = hiltViewModel(),
    carbsButtonsDef: PreferenceSubScreenDef,
    bgInfoState: StateFlow<BgInfoUiState>,
    iobUiState: StateFlow<IobUiState>,
    cobUiState: StateFlow<CobUiState>,
    onNavigateBack: () -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bgInfo by bgInfoState.collectAsStateWithLifecycle()
    val iob by iobUiState.collectAsStateWithLifecycle()
    val cob by cobUiState.collectAsStateWithLifecycle()

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is CarbsDialogViewModel.SideEffect.ShowDeliveryError -> {
                    onShowDeliveryError(effect.comment)
                }

                is CarbsDialogViewModel.SideEffect.ShowNoActionDialog -> {
                    // handled via showNoAction local state
                }
            }
        }
    }

    // Dialog states (rememberSaveable to survive rotation)
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showButtonSettings by rememberSaveable { mutableStateOf(false) }

    // Confirmation dialog
    if (showConfirmation) {
        if (!viewModel.hasAction()) {
            showConfirmation = false
            showNoAction = true
        } else {
            val summaryLines = viewModel.buildConfirmationSummary()
            OkCancelDialog(
                title = stringResource(CoreUiR.string.carbs),
                message = summaryLines.joinToString("<br/>"),
                icon = ElementType.CARBS.icon(),
                iconTint = ElementType.CARBS.color(),
                onConfirm = {
                    viewModel.confirmAndSave()
                    onNavigateBack()
                },
                onDismiss = { showConfirmation = false }
            )
        }
    }

    // No action dialog
    if (showNoAction) {
        OkCancelDialog(
            title = stringResource(CoreUiR.string.carbs),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.CARBS.icon(),
            iconTint = ElementType.CARBS.color(),
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    // Date picker
    if (showDatePicker) {
        EventDatePicker(
            eventTimeMillis = uiState.eventTime,
            onEventTimeChanged = { viewModel.updateEventTime(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time picker
    if (showTimePicker) {
        EventTimePicker(
            eventTimeMillis = uiState.eventTime,
            onEventTimeChanged = { viewModel.updateEventTime(it) },
            onDismiss = { showTimePicker = false }
        )
    }

    // Carbs button settings bottom sheet
    if (showButtonSettings) {
        CarbsButtonSettingsSheet(
            settingsDef = carbsButtonsDef,
            onDismiss = {
                showButtonSettings = false
                viewModel.refreshCarbsButtons()
            }
        )
    }

    CarbsDialogContent(
        uiState = uiState,
        bgInfo = bgInfo,
        iob = iob,
        cob = cob,
        dateString = viewModel.dateUtil.dateString(uiState.eventTime),
        timeString = viewModel.dateUtil.timeString(uiState.eventTime),
        onHypoChange = viewModel::updateHypoTt,
        onEatingSoonChange = viewModel::updateEatingSoonTt,
        onActivityChange = viewModel::updateActivityTt,
        onCarbsChange = { viewModel.updateCarbs(it.toInt()) },
        onAddCarbs = viewModel::addCarbs,
        onTimeOffsetChange = { viewModel.updateTimeOffset(it.toInt()) },
        onAlarmChange = viewModel::updateAlarm,
        onDurationChange = { viewModel.updateDuration(it.toInt()) },
        onBolusReminderChange = viewModel::updateBolusReminder,
        onNotesChange = viewModel::updateNotes,
        onDateClick = { showDatePicker = true },
        onTimeClick = { showTimePicker = true },
        onSettingsClick = if (uiState.simpleMode) null else {
            { showButtonSettings = true }
        },
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true }
    )
}

@Composable
private fun CarbsDialogContent(
    uiState: CarbsDialogUiState,
    bgInfo: BgInfoUiState,
    iob: IobUiState,
    cob: CobUiState,
    dateString: String,
    timeString: String,
    onHypoChange: (Boolean) -> Unit,
    onEatingSoonChange: (Boolean) -> Unit,
    onActivityChange: (Boolean) -> Unit,
    onCarbsChange: (Double) -> Unit,
    onAddCarbs: (Int) -> Unit,
    onTimeOffsetChange: (Double) -> Unit,
    onAlarmChange: (Boolean) -> Unit,
    onDurationChange: (Double) -> Unit,
    onBolusReminderChange: (Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ElementType.CARBS.icon(),
                            contentDescription = null,
                            tint = ElementType.CARBS.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(ElementType.CARBS.labelResId()))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                },
                actions = {
                    if (onSettingsClick != null) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiR.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            val hasAction = uiState.carbs != 0 || uiState.hypoTtChecked || uiState.eatingSoonTtChecked || uiState.activityTtChecked
            Button(
                onClick = onConfirmClick,
                enabled = hasAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (uiState.carbs > 0) {
                    Text(stringResource(CoreUiR.string.format_carbs, uiState.carbs))
                } else {
                    Text(stringResource(CoreUiR.string.ok))
                }
            }
        }
    ) { paddingValues ->
        val itemModifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .clearFocusOnTap(focusManager)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Status Bar ---
            DialogStatusBar(bgInfo = bgInfo, iob = iob, cob = cob)

            // --- Card 1: TT switches ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                TempTargetCheckboxes(
                    hypoChecked = uiState.hypoTtChecked,
                    eatingSoonChecked = uiState.eatingSoonTtChecked,
                    activityChecked = uiState.activityTtChecked,
                    onHypoChange = onHypoChange,
                    onEatingSoonChange = onEatingSoonChange,
                    onActivityChange = onActivityChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // --- Card 2: Carbs + Duration + Bolus Reminder + Carb Time ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Carbs + Quick add
                    Column(modifier = itemModifier) {
                        NumberInputRow(
                            labelResId = CoreUiR.string.carbs,
                            value = uiState.carbs.toDouble(),
                            onValueChange = onCarbsChange,
                            valueRange = (-uiState.maxCarbs).toDouble()..uiState.maxCarbs.toDouble(),
                            step = 1.0,
                            valueFormat = DecimalFormat("0"),
                            unitLabel = stringResource(CoreUiR.string.shortgramm)
                        )
                        QuickAddButtons(
                            increment1 = uiState.carbsButtonIncrement1,
                            increment2 = uiState.carbsButtonIncrement2,
                            increment3 = uiState.carbsButtonIncrement3,
                            onAddCarbs = onAddCarbs
                        )
                    }

                    // Duration
                    NumberInputRow(
                        labelResId = CoreUiR.string.duration_label,
                        value = uiState.durationHours.toDouble(),
                        onValueChange = onDurationChange,
                        valueRange = 0.0..uiState.maxCarbsDurationHours.toDouble(),
                        step = 1.0,
                        valueFormat = DecimalFormat("0"),
                        unitLabel = stringResource(InterfacesR.string.shorthour),
                        modifier = itemModifier
                    )

                    // Bolus Reminder (conditional)
                    if (uiState.showBolusReminder) {
                        Row(
                            modifier = itemModifier
                                .clickable { onBolusReminderChange(!uiState.bolusReminderChecked) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(CoreUiR.string.bolus_reminder),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.bolusReminderChecked,
                                onCheckedChange = { onBolusReminderChange(it) }
                            )
                        }
                    }

                    // Carb time (at bottom)
                    CarbTimeRow(
                        offsetMinutes = uiState.timeOffsetMinutes,
                        alarmChecked = uiState.alarmChecked,
                        onOffsetChange = { onTimeOffsetChange(it.toDouble()) },
                        onAlarmChange = onAlarmChange,
                        resolvedTimeText = if (uiState.timeOffsetMinutes != 0) timeString else null,
                        offsetRange = -7 * 24 * 60..12 * 60,
                        dateTimeContent = {
                            DateTimeSection(
                                dateString = dateString,
                                timeString = timeString,
                                eventTimeChanged = uiState.eventTimeChanged,
                                onDateClick = onDateClick,
                                onTimeClick = onTimeClick
                            )
                        },
                        modifier = itemModifier
                    )

                    // Notes
                    if (uiState.showNotesFromPreferences) {
                        TextField(
                            value = uiState.notes,
                            onValueChange = onNotesChange,
                            label = { Text(stringResource(CoreUiR.string.notes_label)) },
                            modifier = itemModifier,
                            singleLine = false,
                            maxLines = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CarbsDialogScreenPreview() {
    MaterialTheme {
        CarbsDialogContent(
            uiState = CarbsDialogUiState(
                carbs = 15,
                maxCarbs = 100,
                carbsButtonIncrement1 = 5,
                carbsButtonIncrement2 = 10,
                carbsButtonIncrement3 = 20,
                showNotesFromPreferences = true,
                showBolusReminder = true
            ),
            bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
            iob = IobUiState(),
            cob = CobUiState(),
            dateString = "25/02/2026",
            timeString = "14:30",
            onHypoChange = {},
            onEatingSoonChange = {},
            onActivityChange = {},
            onCarbsChange = {},
            onAddCarbs = {},
            onTimeOffsetChange = {},
            onAlarmChange = {},
            onDurationChange = {},
            onBolusReminderChange = {},
            onNotesChange = {},
            onDateClick = {},
            onTimeClick = {},
            onSettingsClick = {},
            onNavigateBack = {},
            onConfirmClick = {}
        )
    }
}


@Composable
private fun TempTargetCheckboxes(
    hypoChecked: Boolean,
    eatingSoonChecked: Boolean,
    activityChecked: Boolean,
    onHypoChange: (Boolean) -> Unit,
    onEatingSoonChange: (Boolean) -> Unit,
    onActivityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onHypoChange(!hypoChecked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(app.aaps.ui.R.string.start_hypo_tt),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = hypoChecked, onCheckedChange = { onHypoChange(it) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEatingSoonChange(!eatingSoonChecked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(app.aaps.ui.R.string.start_eating_soon_tt),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = eatingSoonChecked, onCheckedChange = { onEatingSoonChange(it) })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onActivityChange(!activityChecked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(app.aaps.ui.R.string.start_activity_tt),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = activityChecked, onCheckedChange = { onActivityChange(it) })
        }
    }
}

@Composable
private fun QuickAddButtons(
    increment1: Int,
    increment2: Int,
    increment3: Int,
    onAddCarbs: (Int) -> Unit
) {
    val increments = listOf(increment1, increment2, increment3).filter { it > 0 }
    if (increments.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        increments.forEach { amount ->
            FilledTonalButton(onClick = { onAddCarbs(amount) }) {
                Text("+$amount")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarbsButtonSettingsSheet(
    settingsDef: PreferenceSubScreenDef,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(
                text = stringResource(settingsDef.titleResId),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )
            ProvidePreferenceTheme {
                AdaptivePreferenceList(
                    items = settingsDef.items
                )
            }
        }
    }
}

@Composable
private fun DateTimeSection(
    dateString: String,
    timeString: String,
    eventTimeChanged: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = dateString,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(CoreUiR.string.date)) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = if (eventTimeChanged) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .weight(1f)
                .clickable { onDateClick() },
            singleLine = true
        )

        OutlinedTextField(
            value = timeString,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(CoreUiR.string.time)) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = if (eventTimeChanged) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .weight(1f)
                .clickable { onTimeClick() },
            singleLine = true
        )
    }
}

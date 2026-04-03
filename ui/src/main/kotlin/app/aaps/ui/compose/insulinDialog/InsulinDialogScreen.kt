package app.aaps.ui.compose.insulinDialog

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ICfg
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.DateTimeSection
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
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@Composable
fun InsulinDialogScreen(
    viewModel: InsulinDialogViewModel = hiltViewModel(),
    insulinButtonsDef: PreferenceSubScreenDef,
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

    // Dialog states
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is InsulinDialogViewModel.SideEffect.ShowDeliveryError -> {
                    onShowDeliveryError(effect.comment)
                }

                is InsulinDialogViewModel.SideEffect.ShowNoActionDialog -> {
                    showNoAction = true
                }
            }
        }
    }
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
                title = stringResource(CoreUiR.string.bolus),
                message = summaryLines.joinToString("<br/>"),
                icon = ElementType.INSULIN.icon(),
                iconTint = ElementType.INSULIN.color(),
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
            title = stringResource(CoreUiR.string.bolus),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.INSULIN.icon(),
            iconTint = ElementType.INSULIN.color(),
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

    // Insulin button settings bottom sheet
    if (showButtonSettings) {
        InsulinButtonSettingsSheet(
            settingsDef = insulinButtonsDef,
            onDismiss = {
                showButtonSettings = false
                viewModel.refreshInsulinButtons()
            }
        )
    }

    InsulinDialogContent(
        uiState = uiState,
        bgInfo = bgInfo,
        iob = iob,
        cob = cob,
        dateString = viewModel.dateUtil.dateString(uiState.eventTime),
        timeString = viewModel.dateUtil.timeString(uiState.eventTime),
        bolusFormat = viewModel.decimalFormatter.pumpSupportedBolusFormat(uiState.bolusStep),
        formatAmount = { viewModel.decimalFormatter.toPumpSupportedBolus(it, uiState.bolusStep) },
        onEatingSoonChange = viewModel::updateEatingSoonTt,
        onRecordOnlyChange = viewModel::updateRecordOnly,
        onInsulinChange = { viewModel.updateInsulin(it) },
        onAddInsulin = viewModel::addInsulin,
        onTimeOffsetChange = { viewModel.updateTimeOffset(it.toInt()) },
        onNotesChange = viewModel::updateNotes,
        onDateClick = { showDatePicker = true },
        onTimeClick = { showTimePicker = true },
        onSettingsClick = if (uiState.simpleMode) null else {
            { showButtonSettings = true }
        },
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true },
        onInsulinTypeSelect = viewModel::selectInsulinType
    )
}

@Composable
private fun InsulinDialogContent(
    uiState: InsulinDialogUiState,
    bgInfo: BgInfoUiState,
    iob: IobUiState,
    cob: CobUiState,
    dateString: String,
    timeString: String,
    bolusFormat: DecimalFormat,
    formatAmount: (Double) -> String,
    onEatingSoonChange: (Boolean) -> Unit,
    onRecordOnlyChange: (Boolean) -> Unit,
    onInsulinChange: (Double) -> Unit,
    onAddInsulin: (Double) -> Unit,
    onTimeOffsetChange: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onInsulinTypeSelect: (ICfg) -> Unit,
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
                            imageVector = ElementType.INSULIN.icon(),
                            contentDescription = null,
                            tint = ElementType.INSULIN.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(ElementType.INSULIN.labelResId()))
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
            Button(
                onClick = onConfirmClick,
                enabled = uiState.confirmEnabled,
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
                if (uiState.insulin > 0.0) {
                    Text(stringResource(CoreUiR.string.format_insulin_units, uiState.insulin))
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

            // --- Card 1: Switches ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Eating Soon TT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEatingSoonChange(!uiState.eatingSoonTtChecked) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(app.aaps.ui.R.string.start_eating_soon_tt),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = uiState.eatingSoonTtChecked, onCheckedChange = { onEatingSoonChange(it) })
                    }

                    // Record Only
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = uiState.recordOnlyEnabled) {
                                onRecordOnlyChange(!uiState.recordOnlyChecked)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(CoreUiR.string.bolus_recorded_only),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.forcedRecordOnly) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.recordOnlyChecked,
                            onCheckedChange = { onRecordOnlyChange(it) },
                            enabled = uiState.recordOnlyEnabled
                        )
                    }
                }
            }

            // --- Card 2: Insulin amount ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    NumberInputRow(
                        labelResId = CoreUiR.string.overview_insulin_label,
                        value = uiState.insulin,
                        onValueChange = onInsulinChange,
                        valueRange = 0.0..uiState.maxInsulin,
                        step = uiState.bolusStep,
                        valueFormat = bolusFormat,
                        unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname)
                    )
                    InsulinQuickAddButtons(
                        increment1 = uiState.insulinButtonIncrement1,
                        increment2 = uiState.insulinButtonIncrement2,
                        increment3 = uiState.insulinButtonIncrement3,
                        formatAmount = formatAmount,
                        onAddInsulin = onAddInsulin
                    )
                }
            }

            // --- Card 3: Insulin type selection (visible when recordOnly) ---
            if (uiState.timeLayoutVisible) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(CoreUiR.string.record_insulin_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        var expanded by remember { mutableStateOf(false) }

                        @OptIn(ExperimentalMaterial3Api::class)
                        (ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedIcfg?.insulinLabel ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(CoreUiR.string.select_insulin)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                uiState.insulins.forEach { iCfg ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = iCfg.insulinLabel,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        },
                                        onClick = {
                                            onInsulinTypeSelect(iCfg)
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        })
                    }
                }
            }

            // --- Card 4: Time Selection (visible when recordOnly) ---
            if (uiState.timeLayoutVisible) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        NumberInputRow(
                            labelResId = CoreUiR.string.time,
                            value = uiState.timeOffsetMinutes.toDouble(),
                            onValueChange = onTimeOffsetChange,
                            valueRange = -12.0 * 60..12.0 * 60,
                            step = 5.0,
                            unitLabelResId = KeysR.string.units_min
                        )
                        DateTimeSection(
                            dateString = dateString,
                            timeString = timeString,
                            eventTimeChanged = uiState.eventTimeChanged,
                            onDateClick = onDateClick,
                            onTimeClick = onTimeClick
                        )
                    }
                }
            }

            // --- Notes ---
            if (uiState.showNotesFromPreferences) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    TextField(
                        value = uiState.notes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(CoreUiR.string.notes_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InsulinDialogScreenPreview() {
    MaterialTheme {
        InsulinDialogContent(
            uiState = InsulinDialogUiState(
                insulin = 2.5,
                selectedIcfg = null,
                insulins = ArrayList(),
                maxInsulin = 10.0,
            bolusStep = 0.1,
            insulinButtonIncrement1 = 0.5,
            insulinButtonIncrement2 = 1.0,
            insulinButtonIncrement3 = 2.0,
            showNotesFromPreferences = true
            ),
            bgInfo = BgInfoUiState(bgInfo = null, timeAgoText = ""),
            iob = IobUiState(),
            cob = CobUiState(),
            dateString = "25/02/2026",
            timeString = "14:30",
            bolusFormat = DecimalFormat("0.0"),
            formatAmount = { DecimalFormat("0.0").format(it) },
            onEatingSoonChange = {},
            onRecordOnlyChange = {},
            onInsulinChange = {},
            onAddInsulin = {},
            onTimeOffsetChange = {},
            onNotesChange = {},
            onDateClick = {},
            onTimeClick = {},
            onSettingsClick = null,
            onNavigateBack = {},
            onConfirmClick = {},
            onInsulinTypeSelect = {}
        )
    }
}


@Composable
private fun InsulinQuickAddButtons(
    increment1: Double,
    increment2: Double,
    increment3: Double,
    formatAmount: (Double) -> String,
    onAddInsulin: (Double) -> Unit
) {
    val increments = listOf(increment1, increment2, increment3).filter { it != 0.0 }
    if (increments.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        increments.forEach { amount ->
            val formatted = formatAmount(amount)
            val label = if (amount > 0) "+$formatted" else formatted
            FilledTonalButton(onClick = { onAddInsulin(amount) }) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsulinButtonSettingsSheet(
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


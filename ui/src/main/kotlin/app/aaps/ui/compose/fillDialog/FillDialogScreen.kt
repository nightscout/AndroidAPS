package app.aaps.ui.compose.fillDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.aaps.ui.R
import app.aaps.ui.compose.EventDatePicker
import app.aaps.ui.compose.EventTimePicker
import java.text.DecimalFormat
import app.aaps.core.ui.R as CoreUiR

@Composable
fun FillDialogScreen(
    viewModel: FillDialogViewModel = hiltViewModel(),
    fillButtonsDef: PreferenceSubScreenDef,
    onNavigateBack: () -> Unit,
    onShowSiteRotationDialog: (Long) -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog states (rememberSaveable to survive rotation)
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showNoAction by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showButtonSettings by rememberSaveable { mutableStateOf(false) }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is FillDialogViewModel.SideEffect.ShowSiteRotationDialog -> {
                    onShowSiteRotationDialog(effect.timestamp)
                }

                is FillDialogViewModel.SideEffect.ShowNoActionDialog     -> {
                    showNoAction = true
                }

                is FillDialogViewModel.SideEffect.ShowDeliveryError      -> {
                    onShowDeliveryError(effect.comment)
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmation) {
        val summaryLines = viewModel.buildConfirmationSummary()
        val hasAction = uiState.insulinAfterConstraints > 0 || uiState.siteChange || uiState.insulinCartridgeChange

        if (!hasAction) {
            showConfirmation = false
            showNoAction = true
        } else {
            OkCancelDialog(
                title = stringResource(ElementType.FILL.labelResId()),
                message = summaryLines.joinToString("<br/>"),
                icon = ElementType.FILL.icon(),
                iconTint = ElementType.FILL.color(),
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
            title = stringResource(ElementType.FILL.labelResId()),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = ElementType.FILL.icon(),
            iconTint = ElementType.FILL.color(),
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

    // Preset button settings bottom sheet
    if (showButtonSettings) {
        FillButtonSettingsSheet(
            settingsDef = fillButtonsDef,
            viewModel = viewModel,
            onDismiss = {
                showButtonSettings = false
                viewModel.refreshPresetButtons()
            }
        )
    }

    FillDialogContent(
        uiState = uiState,
        dateString = viewModel.dateUtil.dateString(uiState.eventTime),
        timeString = viewModel.dateUtil.timeString(uiState.eventTime),
        bolusFormat = viewModel.decimalFormat(),
        onSiteChangeClick = { viewModel.updateSiteChange(!uiState.siteChange) },
        onCartridgeChangeClick = { viewModel.updateCartridgeChange(!uiState.insulinCartridgeChange) },
        onInsulinChange = viewModel::updateInsulin,
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
private fun FillDialogContent(
    uiState: FillDialogUiState,
    dateString: String,
    timeString: String,
    bolusFormat: DecimalFormat,
    onSiteChangeClick: () -> Unit,
    onCartridgeChangeClick: () -> Unit,
    onInsulinChange: (Double) -> Unit,
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
                title = { Text(stringResource(ElementType.FILL.labelResId())) },
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
                    IconButton(onClick = onConfirmClick) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(CoreUiR.string.ok),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .clearFocusOnTap(focusManager)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = ElementType.FILL.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Site/cartridge change switches
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSiteChangeClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.record_pump_site_change),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = uiState.siteChange,
                    onCheckedChange = { onSiteChangeClick() }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCartridgeChangeClick() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.record_insulin_cartridge_change),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = uiState.insulinCartridgeChange,
                    onCheckedChange = { onCartridgeChangeClick() }
                )
            }

            if (uiState.showBolus) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Insulin section
                NumberInputRow(
                    labelResId = CoreUiR.string.bolus,
                    value = uiState.insulin,
                    onValueChange = onInsulinChange,
                    valueRange = 0.0..uiState.maxInsulin,
                    step = uiState.bolusStep,
                    valueFormat = bolusFormat,
                    unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname),
                    modifier = Modifier.fillMaxWidth()
                )

                // Preset buttons
                PresetButtonsRow(
                    presetButton1 = uiState.presetButton1,
                    presetButton2 = uiState.presetButton2,
                    presetButton3 = uiState.presetButton3,
                    bolusStep = uiState.bolusStep,
                    onPresetClick = onInsulinChange
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Date/Time Section
            SectionHeader(stringResource(CoreUiR.string.time))
            DateTimeSection(
                dateString = dateString,
                timeString = timeString,
                eventTimeChanged = uiState.eventTimeChanged,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick
            )

            // Notes section
            if (uiState.showNotesFromPreferences) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(CoreUiR.string.notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FillDialogScreenPreview() {
    MaterialTheme {
        FillDialogContent(
            uiState = FillDialogUiState(
                insulin = 0.0,
                siteChange = true,
                insulinCartridgeChange = false,
                maxInsulin = 10.0,
                bolusStep = 0.1,
                presetButton1 = 0.3,
                presetButton2 = 0.5,
                presetButton3 = 1.0,
                showNotesFromPreferences = true
            ),
            dateString = "25/02/2026",
            timeString = "14:30",
            bolusFormat = DecimalFormat("0.0"),
            onSiteChangeClick = {},
            onCartridgeChangeClick = {},
            onInsulinChange = {},
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PresetButtonsRow(
    presetButton1: Double,
    presetButton2: Double,
    presetButton3: Double,
    bolusStep: Double,
    onPresetClick: (Double) -> Unit
) {
    val presets = listOf(presetButton1, presetButton2, presetButton3).filter { it > 0 }
    if (presets.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { amount ->
            val label = if (bolusStep <= 0.051) "%.2f".format(amount) else "%.1f".format(amount)
            FilledTonalButton(onClick = { onPresetClick(amount) }) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FillButtonSettingsSheet(
    settingsDef: PreferenceSubScreenDef,
    viewModel: FillDialogViewModel,
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

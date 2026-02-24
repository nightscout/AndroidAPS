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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import app.aaps.core.ui.compose.icons.IcCanulaChange
import app.aaps.core.ui.compose.preference.AdaptivePreferenceList
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.ui.R
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import app.aaps.core.ui.R as CoreUiR

@Composable
fun FillDialogScreen(
    viewModel: FillDialogViewModel,
    preselect: FillPreselect = FillPreselect.NONE,
    fillButtonsDef: PreferenceSubScreenDef,
    onNavigateBack: () -> Unit,
    onShowSiteRotationDialog: (Long) -> Unit,
    onShowDeliveryError: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Initialize ViewModel
    LaunchedEffect(preselect) {
        viewModel.init(preselect)
    }

    // Dialog states
    var showConfirmation by remember { mutableStateOf(false) }
    var showNoAction by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showButtonSettings by remember { mutableStateOf(false) }

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
                title = stringResource(CoreUiR.string.prime_fill),
                message = summaryLines.joinToString("<br/>"),
                icon = IcCanulaChange,
                iconTint = AapsTheme.elementColors.insulin,
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
            title = stringResource(CoreUiR.string.prime_fill),
            message = stringResource(CoreUiR.string.no_action_selected),
            icon = IcCanulaChange,
            iconTint = AapsTheme.elementColors.insulin,
            onConfirm = { showNoAction = false },
            onDismiss = { showNoAction = false }
        )
    }

    // Date picker
    if (showDatePicker) {
        val tz = TimeZone.currentSystemDefault()
        DatePickerModal(
            onDateSelected = { selectedMillis ->
                selectedMillis?.let {
                    val currentLdt = Instant.fromEpochMilliseconds(uiState.eventTime).toLocalDateTime(tz)
                    val selectedDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date
                    val merged = LocalDateTime(selectedDate, currentLdt.time)
                    viewModel.updateEventTime(merged.toInstant(tz).toEpochMilliseconds())
                }
            },
            onDismiss = { showDatePicker = false },
            initialDateMillis = uiState.eventTime
        )
    }

    // Time picker
    if (showTimePicker) {
        val tz = TimeZone.currentSystemDefault()
        val currentLdt = Instant.fromEpochMilliseconds(uiState.eventTime).toLocalDateTime(tz)
        TimePickerModal(
            onTimeSelected = { hour, minute ->
                val merged = LocalDateTime(currentLdt.date, LocalTime(hour, minute))
                viewModel.updateEventTime(merged.toInstant(tz).toEpochMilliseconds())
            },
            onDismiss = { showTimePicker = false },
            initialHour = currentLdt.hour,
            initialMinute = currentLdt.minute,
            is24Hour = true
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

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiR.string.prime_fill)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showConfirmation = true }) {
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
                    imageVector = IcCanulaChange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Checkboxes section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateSiteChange(!uiState.siteChange) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.siteChange,
                    onCheckedChange = null
                )
                Text(
                    text = stringResource(R.string.record_pump_site_change),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateCartridgeChange(!uiState.insulinCartridgeChange) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.insulinCartridgeChange,
                    onCheckedChange = null
                )
                Text(
                    text = stringResource(R.string.record_insulin_cartridge_change),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Insulin section
            NumberInputRow(
                labelResId = CoreUiR.string.bolus,
                value = uiState.insulin,
                onValueChange = viewModel::updateInsulin,
                valueRange = 0.0..uiState.maxInsulin,
                step = uiState.bolusStep,
                valueFormat = viewModel.decimalFormat(),
                unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname),
                modifier = Modifier.fillMaxWidth()
            )

            // Preset buttons
            PresetButtonsRow(
                presetButton1 = uiState.presetButton1,
                presetButton2 = uiState.presetButton2,
                presetButton3 = uiState.presetButton3,
                bolusStep = uiState.bolusStep,
                onPresetClick = viewModel::updateInsulin,
                onSettingsClick = if (uiState.simpleMode) null else {
                    { showButtonSettings = true }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Date/Time Section
            SectionHeader(stringResource(CoreUiR.string.time))
            DateTimeSection(
                eventTime = uiState.eventTime,
                eventTimeChanged = uiState.eventTimeChanged,
                dateUtil = viewModel.dateUtil,
                onDateClick = { showDatePicker = true },
                onTimeClick = { showTimePicker = true }
            )

            // Notes section
            if (uiState.showNotesFromPreferences) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::updateNotes,
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
    onPresetClick: (Double) -> Unit,
    onSettingsClick: (() -> Unit)?
) {
    val presets = listOf(presetButton1, presetButton2, presetButton3).filter { it > 0 }
    if (presets.isEmpty() && onSettingsClick == null) return

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
        if (onSettingsClick != null) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(CoreUiR.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
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
                    items = settingsDef.items,
                    preferences = viewModel.preferences,
                    config = viewModel.config
                )
            }
        }
    }
}

@Composable
private fun DateTimeSection(
    eventTime: Long,
    eventTimeChanged: Boolean,
    dateUtil: app.aaps.core.interfaces.utils.DateUtil,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = dateUtil.dateString(eventTime),
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
            value = dateUtil.timeString(eventTime),
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

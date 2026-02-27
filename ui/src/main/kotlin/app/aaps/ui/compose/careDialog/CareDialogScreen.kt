package app.aaps.ui.compose.careDialog

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.ui.R
import app.aaps.ui.compose.EventDatePicker
import app.aaps.ui.compose.EventTimePicker
import java.text.DecimalFormat
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@Composable
fun CareDialogScreen(
    viewModel: CareDialogViewModel,
    eventType: UiInteraction.EventType,
    onNavigateBack: () -> Unit,
    onShowSiteRotationDialog: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Initialize ViewModel for this event type
    LaunchedEffect(eventType) {
        viewModel.initForEventType(eventType)
    }

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is CareDialogViewModel.SideEffect.ShowSiteRotationDialog -> {
                    onShowSiteRotationDialog(effect.timestamp)
                }
            }
        }
    }

    // Dialog states (rememberSaveable to survive rotation)
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    // Confirmation dialog
    if (showConfirmation) {
        val summaryLines = viewModel.buildConfirmationSummary()
        OkCancelDialog(
            title = stringResource(eventType.titleResId()),
            message = summaryLines.joinToString("<br/>"),
            icon = eventType.icon(),
            onConfirm = {
                viewModel.confirmAndSave()
                onNavigateBack()
            },
            onDismiss = { showConfirmation = false }
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

    CareDialogContent(
        uiState = uiState,
        eventType = eventType,
        dateString = viewModel.dateUtil.dateString(uiState.eventTime),
        timeString = viewModel.dateUtil.timeString(uiState.eventTime),
        onMeterTypeChange = viewModel::updateMeterType,
        onBgValueChange = viewModel::updateBgValue,
        onDurationChange = viewModel::updateDuration,
        onNotesChange = viewModel::updateNotes,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true },
        onDateClick = { showDatePicker = true },
        onTimeClick = { showTimePicker = true }
    )
}

@Composable
private fun CareDialogContent(
    uiState: CareDialogUiState,
    eventType: UiInteraction.EventType,
    dateString: String,
    timeString: String,
    onMeterTypeChange: (TE.MeterType) -> Unit,
    onBgValueChange: (Double) -> Unit,
    onDurationChange: (Double) -> Unit,
    onNotesChange: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onConfirmClick: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(eventType.titleResId())) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                },
                actions = {
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
                    imageVector = eventType.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // BG Section
            if (uiState.showBgSection) {
                BgSection(
                    meterType = uiState.meterType,
                    bgValue = uiState.bgValue,
                    glucoseUnits = uiState.glucoseUnits,
                    onMeterTypeChange = onMeterTypeChange,
                    onBgValueChange = onBgValueChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Duration Section
            if (uiState.showDurationSection) {
                DurationSection(
                    duration = uiState.duration,
                    onDurationChange = onDurationChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Date/Time Section
            SectionHeader(stringResource(CoreUiR.string.time))
            DateTimeSection(
                dateString = dateString,
                timeString = timeString,
                eventTimeChanged = uiState.eventTimeChanged,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick
            )

            // Notes Section
            if (uiState.showNotesSection) {
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
private fun BgSection(
    meterType: TE.MeterType,
    bgValue: Double,
    glucoseUnits: GlucoseUnit,
    onMeterTypeChange: (TE.MeterType) -> Unit,
    onBgValueChange: (Double) -> Unit
) {
    val meterOptions = listOf(
        TE.MeterType.FINGER to stringResource(R.string.bg_meter),
        TE.MeterType.SENSOR to stringResource(R.string.bg_sensor),
        TE.MeterType.MANUAL to stringResource(R.string.bg_other)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        meterOptions.forEach { (type, label) ->
            Row(
                modifier = Modifier
                    .selectable(
                        selected = meterType == type,
                        onClick = { onMeterTypeChange(type) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = meterType == type,
                    onClick = null
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }

    val (minBg, maxBg, step, format) = when (glucoseUnits) {
        GlucoseUnit.MMOL -> BgParams(2.0, 30.0, 0.1, DecimalFormat("0.0"))
        GlucoseUnit.MGDL -> BgParams(36.0, 500.0, 1.0, DecimalFormat("0"))
    }

    NumberInputRow(
        labelResId = CoreUiR.string.bg_label,
        value = bgValue,
        onValueChange = onBgValueChange,
        valueRange = minBg..maxBg,
        step = step,
        valueFormat = format,
        unitLabel = glucoseUnits.asText,
        modifier = Modifier.fillMaxWidth()
    )
}

private data class BgParams(
    val min: Double,
    val max: Double,
    val step: Double,
    val format: DecimalFormat
)

@Composable
private fun DurationSection(
    duration: Double,
    onDurationChange: (Double) -> Unit
) {
    NumberInputRow(
        labelResId = CoreUiR.string.duration_label,
        value = duration,
        onValueChange = onDurationChange,
        valueRange = 0.0..Constants.MAX_PROFILE_SWITCH_DURATION,
        step = 10.0,
        unitLabelResId = KeysR.string.units_min,
        modifier = Modifier.fillMaxWidth()
    )
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

// Extension functions for EventType mapping

fun UiInteraction.EventType.titleResId(): Int = when (this) {
    UiInteraction.EventType.BGCHECK        -> CoreUiR.string.careportal_bgcheck
    UiInteraction.EventType.SENSOR_INSERT  -> CoreUiR.string.cgm_sensor_insert
    UiInteraction.EventType.BATTERY_CHANGE -> CoreUiR.string.pump_battery_change
    UiInteraction.EventType.NOTE           -> CoreUiR.string.careportal_note
    UiInteraction.EventType.EXERCISE       -> CoreUiR.string.careportal_exercise
    UiInteraction.EventType.QUESTION       -> CoreUiR.string.careportal_question
    UiInteraction.EventType.ANNOUNCEMENT   -> CoreUiR.string.careportal_announcement
}

fun UiInteraction.EventType.icon(): ImageVector = when (this) {
    UiInteraction.EventType.BGCHECK        -> IcBgCheck
    UiInteraction.EventType.SENSOR_INSERT  -> IcCgmInsert
    UiInteraction.EventType.BATTERY_CHANGE -> IcPumpBattery
    UiInteraction.EventType.NOTE           -> IcNote
    UiInteraction.EventType.EXERCISE       -> IcActivity
    UiInteraction.EventType.QUESTION       -> IcQuestion
    UiInteraction.EventType.ANNOUNCEMENT   -> IcAnnouncement
}

@Preview(showBackground = true)
@Composable
private fun CareDialogScreenPreview() {
    MaterialTheme {
        CareDialogContent(
            uiState = CareDialogUiState(
                eventType = UiInteraction.EventType.BGCHECK,
                bgValue = 120.0,
                glucoseUnits = GlucoseUnit.MGDL,
                showNotesFromPreferences = true
            ),
            eventType = UiInteraction.EventType.BGCHECK,
            dateString = "25/02/2026",
            timeString = "14:30",
            onMeterTypeChange = {},
            onBgValueChange = {},
            onDurationChange = {},
            onNotesChange = {},
            onNavigateBack = {},
            onConfirmClick = {},
            onDateClick = {},
            onTimeClick = {}
        )
    }
}

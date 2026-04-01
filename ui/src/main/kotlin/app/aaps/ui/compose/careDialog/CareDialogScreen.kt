package app.aaps.ui.compose.careDialog

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.draw.clip
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.DateTimeSection
import app.aaps.core.ui.compose.EventTimeRow
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
import app.aaps.core.ui.compose.siteRotation.SiteLocationSummary
import app.aaps.ui.R
import app.aaps.ui.compose.EventDatePicker
import app.aaps.ui.compose.EventTimePicker
import java.text.DecimalFormat
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

@Composable
fun CareDialogScreen(
    viewModel: CareDialogViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPickSiteLocation: () -> Unit = {},
    siteLocationResult: Pair<String?, String?>? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Process site location result from picker screen
    LaunchedEffect(siteLocationResult) {
        siteLocationResult?.let { (locationName, arrowName) ->
            if (locationName != null) {
                val location = try {
                    TE.Location.valueOf(locationName)
                } catch (_: Exception) {
                    TE.Location.NONE
                }
                viewModel.updateSiteLocation(location)
            }
            if (arrowName != null) {
                val arrow = try {
                    TE.Arrow.valueOf(arrowName)
                } catch (_: Exception) {
                    TE.Arrow.NONE
                }
                viewModel.updateSiteArrow(arrow)
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
            title = stringResource(uiState.eventType.titleResId()),
            message = summaryLines.joinToString("<br/>"),
            icon = uiState.eventType.icon(),
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
        eventType = uiState.eventType,
        dateString = viewModel.dateUtil.dateString(uiState.eventTime),
        timeString = viewModel.dateUtil.timeString(uiState.eventTime),
        onMeterTypeChange = viewModel::updateMeterType,
        onBgValueChange = viewModel::updateBgValue,
        onDurationChange = viewModel::updateDuration,
        onNotesChange = viewModel::updateNotes,
        onNavigateBack = onNavigateBack,
        onConfirmClick = { showConfirmation = true },
        onDateClick = { showDatePicker = true },
        onTimeClick = { showTimePicker = true },
        onPickSiteLocation = onPickSiteLocation
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
    onTimeClick: () -> Unit,
    onPickSiteLocation: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = eventType.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(eventType.titleResId()))
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
                actions = {}
            )
        },
        bottomBar = {
            Button(
                onClick = onConfirmClick,
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
                Text(stringResource(CoreUiR.string.ok))
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
            // Single card with all inputs
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Site rotation (for SENSOR_INSERT)
                    if (uiState.showSiteRotationSection) {
                        SiteLocationSummary(
                            siteType = TE.Type.SENSOR_CHANGE,
                            lastLocationString = uiState.lastSiteLocationString,
                            selectedLocationString = uiState.selectedSiteLocationString,
                            onPickSiteClick = onPickSiteLocation,
                            modifier = itemModifier
                        )
                    }

                    // BG Section
                    if (uiState.showBgSection) {
                        BgSection(
                            meterType = uiState.meterType,
                            bgValue = uiState.bgValue,
                            glucoseUnits = uiState.glucoseUnits,
                            onMeterTypeChange = onMeterTypeChange,
                            onBgValueChange = onBgValueChange,
                            modifier = itemModifier
                        )
                    }

                    // Duration Section
                    if (uiState.showDurationSection) {
                        DurationSection(
                            duration = uiState.duration,
                            onDurationChange = onDurationChange,
                            modifier = itemModifier
                        )
                    }

                    // Time (collapsible "Now" pattern)
                    EventTimeRow(
                        timeChanged = uiState.eventTimeChanged,
                        displayText = "$dateString $timeString",
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

                    // Notes Section
                    if (uiState.showNotesSection) {
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

@Composable
private fun BgSection(
    meterType: TE.MeterType,
    bgValue: Double,
    glucoseUnits: GlucoseUnit,
    onMeterTypeChange: (TE.MeterType) -> Unit,
    onBgValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
        unitLabel = glucoseUnits.asText
    )
    }
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
    onDurationChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    NumberInputRow(
        labelResId = CoreUiR.string.duration_label,
        value = duration,
        onValueChange = onDurationChange,
        valueRange = 0.0..Constants.MAX_PROFILE_SWITCH_DURATION,
        step = 10.0,
        unitLabelResId = KeysR.string.units_min,
        modifier = modifier
    )
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

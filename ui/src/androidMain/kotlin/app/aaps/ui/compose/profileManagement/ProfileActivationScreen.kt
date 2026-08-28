package app.aaps.ui.compose.profileManagement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.DateTimeSection
import app.aaps.core.ui.compose.EventTimeRow
import app.aaps.core.ui.compose.InsulinSelector
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.bottomBarSafeArea
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import app.aaps.core.ui.compose.rememberBringIntoViewOnExpand
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.R
import app.aaps.ui.UiStrings
import java.util.Calendar

/**
 * Full screen for activating a profile with optional percentage, timeshift, and duration.
 *
 * @param profileName Name of the profile to activate
 * @param currentPercentage Current active percentage (for reuse button)
 * @param currentTimeshiftHours Current active timeshift in hours (for reuse button)
 * @param hasReuseValues Whether reuse button should be shown
 * @param showNotesField Whether to show the notes input field (based on BooleanKey.OverviewShowNotesInDialogs)
 * @param initialTimestamp Initial timestamp (defaults to now)
 * @param rh TextResolver for string resources
 * @param onNavigateBack Callback to navigate back
 * @param checkPumpCompatible Returns whether the profile's basal is deliverable by the current pump
 *        at the given percentage. Re-queried as the percentage changes so the screen can block
 *        activation before the user confirms.
 * @param insulinChoices Non-empty ONLY when no insulin is in force (no running profile and none pending) —
 *        i.e. the first-ever switch. The switch has to record an insulin, and the catalogue is a list to
 *        choose from rather than a source of "the current one", so the user picks and activation stays
 *        disabled until they do. Empty in the normal case, where the master resolves the in-force insulin.
 * @param preselectedInsulin Suggested entry from the most recent profile switch, or null to force a choice.
 * @param onActivate Callback when profile is activated with (duration, percentage, timeshift, withTT, notes, timestamp, timeChanged, iCfg)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileActivationScreen(
    profileName: String,
    currentPercentage: Int = 100,
    currentTimeshiftHours: Int = 0,
    hasReuseValues: Boolean = false,
    showNotesField: Boolean = true,
    initialTimestamp: Long,
    rh: TextResolver,
    onNavigateBack: () -> Unit,
    checkPumpCompatible: (percentage: Int) -> Boolean = { true },
    insulinChoices: List<ICfg> = emptyList(),
    preselectedInsulin: ICfg? = null,
    onActivate: (durationMinutes: Int, percentage: Int, timeshiftHours: Int, withTT: Boolean, notes: String, timestamp: Long, timeChanged: Boolean, iCfg: ICfg?) -> Unit
) {
    val dateUtil = LocalDateUtil.current
    val focusManager = LocalFocusManager.current
    var duration by remember { mutableDoubleStateOf(0.0) }
    var percentage by remember { mutableDoubleStateOf(100.0) }
    var timeshift by remember { mutableDoubleStateOf(0.0) }
    var withTT by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    // Only meaningful when insulinChoices is non-empty; null then means "user hasn't chosen yet".
    var selectedInsulin by remember { mutableStateOf(preselectedInsulin) }
    val insulinChoiceSatisfied = insulinChoices.isEmpty() || selectedInsulin != null

    // Date/time state
    val originalTimestamp = remember { initialTimestamp }
    var eventTime by remember { mutableLongStateOf(initialTimestamp) }
    val eventTimeChanged = eventTime != originalTimestamp
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // TT option only visible when duration > 0 and percentage < 100
    val showTTOption = duration > 0 && percentage < 100

    // Pump compatibility is percentage-aware (basal scales with %). Re-query as the user changes
    // the percentage so the warning + the disabled Activate button track the current selection.
    val percentageInt = percentage.toInt()
    val pumpCompatible = remember(percentageInt, checkPumpCompatible) { checkPumpCompatible(percentageInt) }

    // Date picker modal
    if (showDatePicker) {
        DatePickerModal(
            initialDateMillis = dateUtil.timeStampToUtcDateMillis(eventTime),
            onDateSelected = { selectedMillis ->
                selectedMillis?.let {
                    eventTime = dateUtil.mergeUtcDateToTimestamp(eventTime, it)
                }
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Time picker modal
    if (showTimePicker) {
        val context = LocalContext.current
        val calendar = Calendar.getInstance().apply { timeInMillis = eventTime }
        TimePickerModal(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
            onTimeSelected = { hour, minute ->
                eventTime = dateUtil.mergeHourMinuteToTimestamp(eventTime, hour, minute, true)
            },
            onDismiss = { showTimePicker = false }
        )
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(UiStrings.activate_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(CoreUiStrings.close)
                        )
                    }
                },
                actions = {}
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    // Straight to the single master-authored confirmation (prepare → OkCancel(lines) → commit);
                    // no bespoke screen confirm, so there's one confirm dialog with the per-action icon.
                    onActivate(
                        duration.toInt(),
                        percentage.toInt(),
                        timeshift.toInt(),
                        showTTOption && withTT,
                        notes,
                        eventTime,
                        eventTimeChanged,
                        selectedInsulin
                    )
                },
                enabled = pumpCompatible && insulinChoiceSatisfied,
                modifier = Modifier
                    .fillMaxWidth()
                    .bottomBarSafeArea()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(UiStrings.activate_label))
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
                .clearFocusOnTap(focusManager)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pre-emptive block: the profile's basal can't be delivered by the current pump at the
            // selected percentage, so activation is disabled and the reason is shown up front.
            if (!pumpCompatible) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = stringResource(CoreUiStrings.profile_basal_not_compatible_with_pump),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // No insulin is in force (nothing running, nothing pending) — typically the first-ever switch.
            // The switch must record one, and we never substitute from the catalogue, so ask here instead
            // of letting the master refuse the batch.
            if (insulinChoices.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(CoreUiStrings.profile_switch_select_insulin_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        InsulinSelector(
                            insulins = insulinChoices,
                            selected = selectedInsulin,
                            onSelect = { selectedInsulin = it }
                        )
                    }
                }
            }

            // Single card with all inputs
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Percentage
                    NumberInputRow(
                        labelRef = UiStrings.percentage_label,
                        value = percentage,
                        onValueChange = { percentage = it },
                        valueRange = Constants.CPP_PERCENTAGE_RANGE,
                        step = 5.0,
                        unitLabel = CoreUiStrings.units_percent,
                        modifier = itemModifier
                    )

                    // Duration
                    NumberInputRow(
                        labelRef = CoreUiStrings.duration,
                        value = duration,
                        onValueChange = { duration = it },
                        valueRange = Constants.ACTION_DURATION,
                        step = 10.0,
                        unitLabel = CoreUiStrings.units_min,
                        modifier = itemModifier
                    )

                    // Timeshift (collapsible)
                    var timeshiftExpanded by rememberSaveable { mutableStateOf(false) }
                    val timeshiftExpandRequester = rememberBringIntoViewOnExpand(timeshiftExpanded)
                    Column(modifier = itemModifier.bringIntoViewRequester(timeshiftExpandRequester)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(CoreUiStrings.timeshift_label) + ": ",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${timeshift.toInt()}h",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (timeshift.toInt() != 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!timeshiftExpanded) {
                                FilledTonalButton(onClick = { timeshiftExpanded = true }) {
                                    Text(stringResource(CoreUiStrings.change))
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = timeshiftExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            NumberInputRow(
                                labelRef = CoreUiStrings.timeshift_label,
                                value = timeshift,
                                onValueChange = { timeshift = it },
                                valueRange = Constants.CPP_TIMESHIFT_RANGE,
                                step = 1.0,
                                unitLabel = CoreUiStrings.units_hours
                            )
                        }
                    }

                    // Reuse button
                    if (hasReuseValues && (currentPercentage != 100 || currentTimeshiftHours != 0)) {
                        FilledTonalButton(
                            onClick = {
                                percentage = currentPercentage.toDouble()
                                timeshift = currentTimeshiftHours.toDouble()
                            },
                            modifier = itemModifier
                        ) {
                            Text(rh.gs(UiStrings.reuse_profile_pct_hours, currentPercentage, currentTimeshiftHours))
                        }
                    }

                    // Temporary Target switch (only when duration > 0 and percentage < 100)
                    if (showTTOption) {
                        Row(
                            modifier = itemModifier
                                .clickable { withTT = !withTT },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(CoreUiStrings.temporary_target),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(CoreUiStrings.activity),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = withTT,
                                onCheckedChange = { withTT = it }
                            )
                        }
                    }

                    // Time (collapsible "Now" pattern)
                    EventTimeRow(
                        timeChanged = eventTimeChanged,
                        displayText = "${dateUtil.dateString(eventTime)} ${dateUtil.timeString(eventTime)}",
                        dateTimeContent = {
                            DateTimeSection(
                                dateString = dateUtil.dateString(eventTime),
                                timeString = dateUtil.timeString(eventTime),
                                eventTimeChanged = eventTimeChanged,
                                onDateClick = { showDatePicker = true },
                                onTimeClick = { showTimePicker = true }
                            )
                        },
                        modifier = itemModifier
                    )

                    // Notes
                    if (showNotesField) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(CoreUiStrings.notes_label)) },
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

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.DateTimeSection
import app.aaps.core.ui.compose.EventTimeRow
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.TimePickerModal
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.R
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
 * @param dateUtil DateUtil for formatting dates/times
 * @param rh ResourceHelper for string resources
 * @param onNavigateBack Callback to navigate back
 * @param onActivate Callback when profile is activated with (duration, percentage, timeshift, withTT, notes, timestamp, timeChanged)
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
    rh: ResourceHelper,
    onNavigateBack: () -> Unit,
    onActivate: (durationMinutes: Int, percentage: Int, timeshiftHours: Int, withTT: Boolean, notes: String, timestamp: Long, timeChanged: Boolean) -> Unit
) {
    val dateUtil = LocalDateUtil.current
    val focusManager = LocalFocusManager.current
    var duration by remember { mutableDoubleStateOf(0.0) }
    var percentage by remember { mutableDoubleStateOf(100.0) }
    var timeshift by remember { mutableDoubleStateOf(0.0) }
    var withTT by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Date/time state
    val originalTimestamp = remember { initialTimestamp }
    var eventTime by remember { mutableLongStateOf(initialTimestamp) }
    val eventTimeChanged = eventTime != originalTimestamp
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // TT option only visible when duration > 0 and percentage < 100
    val showTTOption = duration > 0 && percentage < 100

    // Format duration as "Xh Ym" when >= 60 minutes
    val durationMinutes = duration.toInt()
    val formattedDuration = if (durationMinutes >= 60) {
        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        rh.gs(app.aaps.core.ui.R.string.format_hour_minute, hours, mins)
    } else {
        rh.gs(app.aaps.core.ui.R.string.format_mins, durationMinutes)
    }

    // Build confirmation message
    val confirmationMessage = buildString {
        append(rh.gs(app.aaps.core.ui.R.string.profile))
        append(": ")
        append(profileName)
        if (duration > 0) {
            append("<br/>")
            append(rh.gs(app.aaps.core.ui.R.string.duration))
            append(": ")
            append(formattedDuration)
        }
        if (percentage.toInt() != 100) {
            append("<br/>")
            append(rh.gs(app.aaps.core.ui.R.string.percent))
            append(": ")
            append("${percentage.toInt()}%")
        }
        if (timeshift.toInt() != 0) {
            append("<br/>")
            append(rh.gs(R.string.timeshift_label))
            append(": ")
            append(rh.gs(app.aaps.core.ui.R.string.format_hours, timeshift))
        }
        if (showTTOption && withTT) {
            append("<br/>")
            append(rh.gs(app.aaps.core.ui.R.string.temporary_target))
            append(": ")
            append(rh.gs(app.aaps.core.ui.R.string.activity))
        }
        if (notes.isNotBlank()) {
            append("<br/>")
            append(rh.gs(app.aaps.core.ui.R.string.notes_label))
            append(": ")
            append(notes)
        }
        if (eventTimeChanged) {
            append("<br/>")
            append(rh.gs(app.aaps.core.ui.R.string.time))
            append(": ")
            append(dateUtil.dateAndTimeString(eventTime))
        }
    }

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

    // Confirmation dialog
    if (showConfirmDialog) {
        OkCancelDialog(
            title = rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
            message = confirmationMessage,
            onConfirm = {
                showConfirmDialog = false
                onActivate(
                    duration.toInt(),
                    percentage.toInt(),
                    timeshift.toInt(),
                    showTTOption && withTT,
                    notes,
                    eventTime,
                    eventTimeChanged
                )
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                            contentDescription = null,
                            tint = ElementType.PROFILE_MANAGEMENT.color()
                        )
                        Column {
                            Text(stringResource(R.string.activate_label))
                            Text(
                                text = profileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {}
            )
        },
        bottomBar = {
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.activate_label))
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
            // Single card with all inputs
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Percentage
                    NumberInputRow(
                        labelResId = R.string.percentage_label,
                        value = percentage,
                        onValueChange = { percentage = it },
                        valueRange = Constants.CPP_MIN_PERCENTAGE.toDouble()..Constants.CPP_MAX_PERCENTAGE.toDouble(),
                        step = 5.0,
                        unitLabelResId = app.aaps.core.keys.R.string.units_percent,
                        modifier = itemModifier
                    )

                    // Duration
                    NumberInputRow(
                        labelResId = app.aaps.core.ui.R.string.duration,
                        value = duration,
                        onValueChange = { duration = it },
                        valueRange = 0.0..Constants.MAX_PROFILE_SWITCH_DURATION,
                        step = 10.0,
                        unitLabelResId = app.aaps.core.keys.R.string.units_min,
                        modifier = itemModifier
                    )

                    // Timeshift (collapsible)
                    var timeshiftExpanded by rememberSaveable { mutableStateOf(false) }
                    Column(modifier = itemModifier) {
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
                                    text = stringResource(R.string.timeshift_label) + ": ",
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
                                    Text(stringResource(app.aaps.core.ui.R.string.change))
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = timeshiftExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            NumberInputRow(
                                labelResId = R.string.timeshift_label,
                                value = timeshift,
                                onValueChange = { timeshift = it },
                                valueRange = Constants.CPP_MIN_TIMESHIFT.toDouble()..Constants.CPP_MAX_TIMESHIFT.toDouble(),
                                step = 1.0,
                                unitLabelResId = app.aaps.core.keys.R.string.units_hours
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
                            Text(rh.gs(R.string.reuse_profile_pct_hours, currentPercentage, currentTimeshiftHours))
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
                                    text = stringResource(app.aaps.core.ui.R.string.temporary_target),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(app.aaps.core.ui.R.string.activity),
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
                            label = { Text(stringResource(app.aaps.core.ui.R.string.notes_label)) },
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

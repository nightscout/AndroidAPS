package app.aaps.ui.compose.profileManagement

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.DatePickerModal
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.TimePickerModal
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
    dateUtil: DateUtil,
    rh: ResourceHelper,
    onNavigateBack: () -> Unit,
    onActivate: (durationMinutes: Int, percentage: Int, timeshiftHours: Int, withTT: Boolean, notes: String, timestamp: Long, timeChanged: Boolean) -> Unit
) {
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
                    Column {
                        Text(stringResource(R.string.activate_label))
                        Text(
                            text = profileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                actions = {
                    Button(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(stringResource(R.string.activate_label))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clearFocusOnTap(focusManager)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Date/Time selection row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date field
                OutlinedTextField(
                    value = dateUtil.dateString(eventTime),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(app.aaps.core.ui.R.string.date)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = if (eventTimeChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true },
                    singleLine = true
                )

                // Time field
                OutlinedTextField(
                    value = dateUtil.timeString(eventTime),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(app.aaps.core.ui.R.string.time)) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = if (eventTimeChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTimePicker = true },
                    singleLine = true
                )
            }

            // Duration input - automatically formats as "Xh Ym" when >= 60
            NumberInputRow(
                labelResId = app.aaps.core.ui.R.string.duration,
                value = duration,
                onValueChange = { duration = it },
                valueRange = 0.0..Constants.MAX_PROFILE_SWITCH_DURATION,
                step = 10.0,
                controlPoints = listOf(
                    0.0 to 0.0,             // 0% slider -> 0h
                    0.25 to 6.0 * 60.0,     // 25% slider -> 6h
                    0.5 to 24.0 * 60.0,     // 50% slider -> 24h
                    0.75 to 48.0 * 60.0,    // 75% slider -> 48h
                    1.0 to Constants.MAX_PROFILE_SWITCH_DURATION   // 100% slider -> 168h
                ),
                unitLabelResId = app.aaps.core.keys.R.string.units_min
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Percentage input
            NumberInputRow(
                labelResId = R.string.percentage_label,
                value = percentage,
                onValueChange = { percentage = it },
                valueRange = Constants.CPP_MIN_PERCENTAGE.toDouble()..Constants.CPP_MAX_PERCENTAGE.toDouble(),
                step = 5.0,
                unitLabelResId = app.aaps.core.keys.R.string.units_percent
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Timeshift input
            NumberInputRow(
                labelResId = R.string.timeshift_label,
                value = timeshift,
                onValueChange = { timeshift = it },
                valueRange = Constants.CPP_MIN_TIMESHIFT.toDouble()..Constants.CPP_MAX_TIMESHIFT.toDouble(),
                step = 1.0,
                unitLabelResId = app.aaps.core.keys.R.string.units_hours
            )

            // Reuse button
            if (hasReuseValues && (currentPercentage != 100 || currentTimeshiftHours != 0)) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        percentage = currentPercentage.toDouble()
                        timeshift = currentTimeshiftHours.toDouble()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(rh.gs(R.string.reuse_profile_pct_hours, currentPercentage, currentTimeshiftHours))
                }
            }

            // Temporary Target switch (only when duration > 0 and percentage < 100)
            if (showTTOption) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
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

            // Notes (conditional based on BooleanKey.OverviewShowNotesInDialogs)
            if (showNotesField) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(app.aaps.core.ui.R.string.notes_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

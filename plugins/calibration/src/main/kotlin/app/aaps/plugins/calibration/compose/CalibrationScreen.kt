package app.aaps.plugins.calibration.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.navigation.LocalPluginNavigationRequest
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.plugins.calibration.CalibrationFit
import app.aaps.plugins.calibration.FitMode
import app.aaps.plugins.calibration.R
import kotlin.math.roundToInt

@Composable
internal fun CalibrationScreen(
    viewModel: CalibrationViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = stringResource(R.string.linear_calibration_name)
    val backDesc = stringResource(app.aaps.core.ui.R.string.back)

    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = {}
            )
        )
    }

    val navigationRequest = LocalPluginNavigationRequest.current
    CalibrationScreenContent(
        state = state,
        formatDateTime = viewModel.dateUtil::dateAndTimeString,
        formatTime = viewModel.dateUtil::timeString,
        onMarkSensorChange = { navigationRequest(NavigationRequest.Element(ElementType.SENSOR_INSERT)) },
        onAddCalibration = { navigationRequest(NavigationRequest.Element(ElementType.CALIBRATION)) },
        onSelectEntry = viewModel::selectEntry,
        onDeleteEntry = viewModel::deleteEntry
    )
}

@Composable
internal fun CalibrationScreenContent(
    state: CalibrationUiState,
    formatDateTime: (Long) -> String,
    formatTime: (Long) -> String,
    onMarkSensorChange: () -> Unit,
    onAddCalibration: () -> Unit,
    onSelectEntry: (Long) -> Unit,
    onDeleteEntry: (Long) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedEntryId, state.entries) {
        val id = state.selectedEntryId ?: return@LaunchedEffect
        val index = state.entries.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        StatusCard(state = state, formatDateTime = formatDateTime, formatTime = formatTime)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Button(
                onClick = onMarkSensorChange,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cal_mark_sensor_change_now))
            }
            Button(
                onClick = onAddCalibration,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cal_add_calibration))
            }
        }

        ChartCard(
            state = state,
            formatDateTime = formatDateTime,
            onSelectEntry = onSelectEntry
        )

        Text(
            text = stringResource(R.string.cal_entries_header, state.entries.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = AapsSpacing.small, top = AapsSpacing.small)
        )

        if (state.entries.isEmpty()) {
            EmptyEntries()
        } else {
            EntriesList(
                entries = state.entries,
                selectedEntryId = state.selectedEntryId,
                glucoseUnit = state.glucoseUnit,
                listState = listState,
                contentPadding = PaddingValues(bottom = AapsSpacing.large),
                formatTime = formatDateTime,
                onSelect = onSelectEntry,
                onDelete = { pendingDeleteId = it }
            )
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.cal_remove_entry_title)) },
            text = { Text(stringResource(R.string.cal_remove_entry_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteEntry(id)
                    pendingDeleteId = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true, name = "Calibration applied")
@Composable
private fun CalibrationScreenContentPreview() {
    val now = 1_700_000_000_000L
    val hour = 3_600_000L
    val entries = listOf(
        CAL(id = 1, timestamp = now - 5 * hour, fingerstickMgdl = 120.0, sensorMgdlAtPairing = 110.0),
        CAL(id = 2, timestamp = now - 3 * hour, fingerstickMgdl = 150.0, sensorMgdlAtPairing = 145.0),
        CAL(id = 3, timestamp = now - 1 * hour, fingerstickMgdl = 95.0, sensorMgdlAtPairing = 90.0)
    )
    MaterialTheme {
        CalibrationScreenContent(
            state = CalibrationUiState(
                sessionStart = now - 6 * hour,
                warmUpEndsAt = now - 4 * hour,
                isInWarmUp = false,
                entries = entries,
                fit = CalibrationFit(slope = 1.05, offset = 2.0, mode = FitMode.Full),
                now = now,
                selectedEntryId = 3,
                glucoseUnit = GlucoseUnit.MGDL
            ),
            formatDateTime = { "01 Jan 12:00" },
            formatTime = { "14:00" },
            onMarkSensorChange = {},
            onAddCalibration = {},
            onSelectEntry = {},
            onDeleteEntry = {}
        )
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true, name = "No session")
@Composable
private fun CalibrationScreenContentNoSessionPreview() {
    MaterialTheme {
        CalibrationScreenContent(
            state = CalibrationUiState(),
            formatDateTime = { "" },
            formatTime = { "" },
            onMarkSensorChange = {},
            onAddCalibration = {},
            onSelectEntry = {},
            onDeleteEntry = {}
        )
    }
}

@Composable
private fun StatusCard(
    state: CalibrationUiState,
    formatDateTime: (Long) -> String,
    formatTime: (Long) -> String
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.medium)) {
            val statusText = when {
                state.sessionStart == null             ->
                    stringResource(R.string.cal_status_no_session)

                state.isInWarmUp                       ->
                    stringResource(R.string.cal_status_warmup, formatTime(state.warmUpEndsAt ?: 0L))

                state.fit == null                      ->
                    stringResource(R.string.cal_status_need_more_entries, state.entries.size)

                !state.fit.isApplicable                ->
                    stringResource(R.string.cal_status_unsafe_fit)

                state.fit.mode == FitMode.OffsetOnly   ->
                    stringResource(R.string.cal_status_applied_offset_only)

                state.fit.mode == FitMode.SlopeClamped ->
                    stringResource(R.string.cal_status_applied_slope_clamped)

                else                                   ->
                    stringResource(R.string.cal_status_applied)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            state.fit?.let { fit ->
                Spacer(Modifier.height(AapsSpacing.small))
                Text(
                    text = stringResource(
                        R.string.cal_slope_offset,
                        fit.slope,
                        fit.correctionAtCenter.formatBgDisplay(state.glucoseUnit, signed = true)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.sessionStart?.let { start ->
                Spacer(Modifier.height(AapsSpacing.small))
                Text(
                    text = stringResource(R.string.cal_session_started, formatDateTime(start)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartCard(
    state: CalibrationUiState,
    formatDateTime: (Long) -> String,
    onSelectEntry: (Long) -> Unit
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.medium)) {
            CalibrationScatterChart(
                entries = state.entries,
                fit = state.fit,
                selectedEntryId = state.selectedEntryId,
                now = state.now,
                glucoseUnit = state.glucoseUnit
            )
            if (state.entries.size >= 2) {
                Spacer(Modifier.height(AapsSpacing.small))
                EntrySliderReadout(state = state, formatDateTime = formatDateTime)
                EntrySlider(state = state, onSelectEntry = onSelectEntry)
            } else if (state.entries.size == 1) {
                Spacer(Modifier.height(AapsSpacing.small))
                EntrySliderReadout(state = state, formatDateTime = formatDateTime)
            }
        }
    }
}

@Composable
private fun EntrySliderReadout(
    state: CalibrationUiState,
    formatDateTime: (Long) -> String
) {
    val selectedIndex = state.entries.indexOfFirst { it.id == state.selectedEntryId }
    if (selectedIndex < 0) return
    val entry = state.entries[selectedIndex]
    Text(
        text = stringResource(
            R.string.cal_chart_entry_readout,
            selectedIndex + 1,
            state.entries.size,
            formatDateTime(entry.timestamp),
            entry.sensorMgdlAtPairing.formatBgDisplay(state.glucoseUnit),
            entry.fingerstickMgdl.formatBgDisplay(state.glucoseUnit)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun Double.formatBgDisplay(unit: GlucoseUnit, signed: Boolean = false): String {
    val converted = if (unit == GlucoseUnit.MMOL) this * Constants.MGDL_TO_MMOLL else this
    val format = when {
        signed && unit == GlucoseUnit.MGDL -> "%+.0f"
        signed && unit == GlucoseUnit.MMOL -> "%+.1f"
        unit == GlucoseUnit.MGDL           -> "%.0f"
        else                               -> "%.1f"
    }
    return format.format(converted)
}

@Composable
private fun EntrySlider(
    state: CalibrationUiState,
    onSelectEntry: (Long) -> Unit
) {
    val selectedIndex = state.entries.indexOfFirst { it.id == state.selectedEntryId }
        .coerceAtLeast(0)
    val lastIndex = state.entries.lastIndex
    Slider(
        value = selectedIndex.toFloat(),
        valueRange = 0f..lastIndex.toFloat(),
        steps = (lastIndex - 1).coerceAtLeast(0),
        onValueChange = { v ->
            val newIndex = v.roundToInt().coerceIn(0, lastIndex)
            val id = state.entries[newIndex].id
            if (id != state.selectedEntryId) onSelectEntry(id)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EmptyEntries() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AapsSpacing.xxLarge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.cal_no_entries),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EntriesList(
    entries: List<CAL>,
    selectedEntryId: Long?,
    glucoseUnit: GlucoseUnit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    formatTime: (Long) -> String,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
    ) {
        items(items = entries, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                selected = entry.id == selectedEntryId,
                glucoseUnit = glucoseUnit,
                formatTime = formatTime,
                onSelect = onSelect,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: CAL,
    selected: Boolean,
    glucoseUnit: GlucoseUnit,
    formatTime: (Long) -> String,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    AapsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(entry.id) },
        selected = selected
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTime(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.cal_entry_pair,
                        entry.fingerstickMgdl.formatBgDisplay(glucoseUnit),
                        entry.sensorMgdlAtPairing.formatBgDisplay(glucoseUnit),
                        (entry.fingerstickMgdl - entry.sensorMgdlAtPairing).formatBgDisplay(glucoseUnit, signed = true)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = { onDelete(entry.id) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.delete),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

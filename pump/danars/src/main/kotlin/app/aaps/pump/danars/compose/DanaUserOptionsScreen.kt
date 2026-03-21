package app.aaps.pump.danars.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.pump.dana.R

@Composable
fun DanaUserOptionsScreen(
    viewModel: DanaUserOptionsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Time format
        SwitchRow(
            label = stringResource(R.string.danar_time_display),
            summary = if (state.timeFormat24h) stringResource(R.string.time_format_24h) else stringResource(R.string.time_format_12h),
            checked = state.timeFormat24h,
            onCheckedChange = viewModel::updateTimeFormat
        )

        HorizontalDivider()

        // Button scroll
        SwitchRow(
            label = stringResource(R.string.danar_button_scroll),
            summary = if (state.buttonScroll) stringResource(R.string.option_on) else stringResource(R.string.option_off),
            checked = state.buttonScroll,
            onCheckedChange = viewModel::updateButtonScroll
        )

        HorizontalDivider()

        // Beep on press
        SwitchRow(
            label = stringResource(R.string.danar_beep),
            summary = if (state.beepOnPress) stringResource(R.string.option_on) else stringResource(R.string.option_off),
            checked = state.beepOnPress,
            onCheckedChange = viewModel::updateBeepOnPress
        )

        HorizontalDivider()

        // Alarm mode
        Text(
            text = stringResource(R.string.danar_pump_alarm),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Column(modifier = Modifier.selectableGroup()) {
            AlarmRadioOption(
                label = stringResource(R.string.danar_pump_alarm_sound),
                selected = state.alarmMode == 1,
                onClick = { viewModel.updateAlarmMode(1) }
            )
            AlarmRadioOption(
                label = stringResource(R.string.danar_pump_alarm_vibrate),
                selected = state.alarmMode == 2,
                onClick = { viewModel.updateAlarmMode(2) }
            )
            AlarmRadioOption(
                label = stringResource(R.string.danar_pump_alarm_both),
                selected = state.alarmMode == 3,
                onClick = { viewModel.updateAlarmMode(3) }
            )
        }

        HorizontalDivider()

        // Screen timeout
        NumberInputRow(
            labelResId = R.string.danar_screen_timeout,
            value = state.screenTimeout.toDouble(),
            onValueChange = viewModel::updateScreenTimeout,
            valueRange = 5.0..240.0,
            step = 5.0,
            formatAsInt = true,
            unitLabelResId = app.aaps.core.keys.R.string.units_sec
        )

        // Backlight
        NumberInputRow(
            labelResId = R.string.danar_backlight,
            value = state.backlight.toDouble(),
            onValueChange = viewModel::updateBacklight,
            valueRange = state.minBacklight.toDouble()..60.0,
            step = 1.0,
            formatAsInt = true,
            unitLabelResId = app.aaps.core.keys.R.string.units_sec
        )

        HorizontalDivider()

        // Glucose units
        SwitchRow(
            label = stringResource(R.string.danar_glucose_units),
            summary = if (state.glucoseUnitMmol) "mmol/L" else "mg/dL",
            checked = state.glucoseUnitMmol,
            onCheckedChange = viewModel::updateGlucoseUnit
        )

        HorizontalDivider()

        // Shutdown hour
        NumberInputRow(
            labelResId = R.string.danar_shutdown,
            value = state.shutdownHour.toDouble(),
            onValueChange = viewModel::updateShutdownHour,
            valueRange = 0.0..24.0,
            step = 1.0,
            formatAsInt = true,
            unitLabelResId = app.aaps.core.keys.R.string.units_hours
        )

        // Low reservoir
        NumberInputRow(
            labelResId = R.string.danar_low_reservoir,
            value = state.lowReservoir.toDouble(),
            onValueChange = viewModel::updateLowReservoir,
            valueRange = 10.0..50.0,
            step = 10.0,
            formatAsInt = true,
            unitLabel = "U"
        )

        // Save button
        Button(
            onClick = { viewModel.save() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.danar_save_user_options))
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AlarmRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

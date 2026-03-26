package app.aaps.pump.dana.compose

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.pump.dana.R

@Composable
fun DanaUserOptionsScreen(
    viewModel: DanaUserOptionsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DanaUserOptionsContent(
        state = state,
        onTimeFormatChange = viewModel::updateTimeFormat,
        onButtonScrollChange = viewModel::updateButtonScroll,
        onBeepChange = viewModel::updateBeepOnPress,
        onAlarmModeChange = viewModel::updateAlarmMode,
        onScreenTimeoutChange = viewModel::updateScreenTimeout,
        onBacklightChange = viewModel::updateBacklight,
        onGlucoseUnitChange = viewModel::updateGlucoseUnit,
        onShutdownHourChange = viewModel::updateShutdownHour,
        onLowReservoirChange = viewModel::updateLowReservoir,
        onSave = viewModel::save
    )
}

@Composable
private fun DanaUserOptionsContent(
    state: UserOptionsUiState,
    onTimeFormatChange: (Boolean) -> Unit = {},
    onButtonScrollChange: (Boolean) -> Unit = {},
    onBeepChange: (Boolean) -> Unit = {},
    onAlarmModeChange: (Int) -> Unit = {},
    onScreenTimeoutChange: (Double) -> Unit = {},
    onBacklightChange: (Double) -> Unit = {},
    onGlucoseUnitChange: (Boolean) -> Unit = {},
    onShutdownHourChange: (Double) -> Unit = {},
    onLowReservoirChange: (Double) -> Unit = {},
    onSave: () -> Unit = {}
) {
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
            onCheckedChange = onTimeFormatChange
        )

        HorizontalDivider()

        // Button scroll
        SwitchRow(
            label = stringResource(R.string.danar_button_scroll),
            summary = if (state.buttonScroll) stringResource(R.string.option_on) else stringResource(R.string.option_off),
            checked = state.buttonScroll,
            onCheckedChange = onButtonScrollChange
        )

        HorizontalDivider()

        // Beep on press
        SwitchRow(
            label = stringResource(R.string.danar_beep),
            summary = if (state.beepOnPress) stringResource(R.string.option_on) else stringResource(R.string.option_off),
            checked = state.beepOnPress,
            onCheckedChange = onBeepChange
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
                onClick = { onAlarmModeChange(1) }
            )
            AlarmRadioOption(
                label = stringResource(R.string.danar_pump_alarm_vibrate),
                selected = state.alarmMode == 2,
                onClick = { onAlarmModeChange(2) }
            )
            AlarmRadioOption(
                label = stringResource(R.string.danar_pump_alarm_both),
                selected = state.alarmMode == 3,
                onClick = { onAlarmModeChange(3) }
            )
        }

        HorizontalDivider()

        // Screen timeout
        NumberInputRow(
            labelResId = R.string.danar_screen_timeout,
            value = state.screenTimeout.toDouble(),
            onValueChange = onScreenTimeoutChange,
            valueRange = 5.0..240.0,
            step = 5.0,
            formatAsInt = true,
            unitLabelResId = app.aaps.core.keys.R.string.units_sec
        )

        // Backlight
        NumberInputRow(
            labelResId = R.string.danar_backlight,
            value = state.backlight.toDouble(),
            onValueChange = onBacklightChange,
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
            onCheckedChange = onGlucoseUnitChange
        )

        HorizontalDivider()

        // Shutdown hour
        NumberInputRow(
            labelResId = R.string.danar_shutdown,
            value = state.shutdownHour.toDouble(),
            onValueChange = onShutdownHourChange,
            valueRange = 0.0..24.0,
            step = 1.0,
            formatAsInt = true,
            unitLabelResId = app.aaps.core.keys.R.string.units_hours
        )

        // Low reservoir
        NumberInputRow(
            labelResId = R.string.danar_low_reservoir,
            value = state.lowReservoir.toDouble(),
            onValueChange = onLowReservoirChange,
            valueRange = 10.0..50.0,
            step = 10.0,
            formatAsInt = true,
            unitLabel = "U"
        )

        // Save button
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.danar_save_user_options))
        }
    }
}

@Preview(showBackground = true, name = "User Options")
@Composable
private fun DanaUserOptionsPreview() {
    MaterialTheme {
        DanaUserOptionsContent(
            state = UserOptionsUiState(
                timeFormat24h = true,
                buttonScroll = false,
                beepOnPress = true,
                alarmMode = 1,
                screenTimeout = 15,
                backlight = 5,
                glucoseUnitMmol = false,
                shutdownHour = 0,
                lowReservoir = 20,
                minBacklight = 1
            )
        )
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

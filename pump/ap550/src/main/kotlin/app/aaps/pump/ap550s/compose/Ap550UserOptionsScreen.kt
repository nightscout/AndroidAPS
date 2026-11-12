package app.aaps.pump.ap550s.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.ap550s.R
import kotlin.math.roundToInt
import app.aaps.core.ui.R as CoreUiR

@Composable
fun Ap550UserOptionsScreen(
    viewModel: Ap550UserOptionsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SwitchRow(
                    label = stringResource(R.string.ap550_time_display),
                    onText = stringResource(R.string.time_format_24h),
                    offText = stringResource(R.string.time_format_12h),
                    checked = state.timeDisplayType24,
                    onCheckedChange = viewModel::updateTimeDisplayType24
                )
                HorizontalDivider()
                SwitchRow(
                    label = stringResource(R.string.ap550_button_scroll),
                    onText = stringResource(R.string.option_on),
                    offText = stringResource(R.string.option_off),
                    checked = state.buttonScrollOnOff,
                    onCheckedChange = viewModel::updateButtonScrollOnOff
                )
                HorizontalDivider()
                SwitchRow(
                    label = stringResource(R.string.ap550_beep),
                    onText = stringResource(R.string.option_on),
                    offText = stringResource(R.string.option_off),
                    checked = state.beepEnabled,
                    onCheckedChange = viewModel::updateBeepEnabled
                )
                HorizontalDivider()
                SwitchRow(
                    label = stringResource(R.string.ap550_glucose_units),
                    onText = stringResource(CoreUiR.string.mmol),
                    offText = stringResource(CoreUiR.string.mgdl),
                    checked = state.mmol,
                    onCheckedChange = viewModel::updateMmol
                )
            }
        }

        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = stringResource(R.string.ap550_pump_alarm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = AapsSpacing.extraLarge, top = AapsSpacing.extraLarge)
                )
                RadioRow(
                    label = stringResource(R.string.ap550_pump_alarm_sound),
                    selected = state.alarm == Ap550UserOptionsViewModel.ALARM_SOUND,
                    onSelect = { viewModel.updateAlarm(Ap550UserOptionsViewModel.ALARM_SOUND) }
                )
                RadioRow(
                    label = stringResource(R.string.ap550_pump_alarm_vibrate),
                    selected = state.alarm == Ap550UserOptionsViewModel.ALARM_VIBRATE,
                    onSelect = { viewModel.updateAlarm(Ap550UserOptionsViewModel.ALARM_VIBRATE) }
                )
                RadioRow(
                    label = stringResource(R.string.ap550_pump_alarm_both),
                    selected = state.alarm == Ap550UserOptionsViewModel.ALARM_BOTH,
                    onSelect = { viewModel.updateAlarm(Ap550UserOptionsViewModel.ALARM_BOTH) }
                )
            }
        }

        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SliderRow(
                    label = stringResource(R.string.ap550_screen_timeout),
                    value = state.lcdOnTimeSec,
                    range = LCD_ON_TIME_RANGE,
                    step = LCD_ON_TIME_STEP,
                    onValueChange = viewModel::updateLcdOnTimeSec
                )
                SliderRow(
                    label = stringResource(R.string.ap550_back_light),
                    value = state.backlightOnTimeSec,
                    range = viewModel.minBacklightOnTimeSec..BACKLIGHT_ON_TIME_MAX,
                    step = 1,
                    onValueChange = viewModel::updateBacklightOnTimeSec
                )
                SliderRow(
                    label = stringResource(R.string.ap550_shutdown),
                    value = state.shutdownHour,
                    range = SHUTDOWN_HOUR_RANGE,
                    step = 1,
                    onValueChange = viewModel::updateShutdownHour
                )
                SliderRow(
                    label = stringResource(R.string.ap550_low_reservoir),
                    value = state.lowReservoirRate,
                    range = LOW_RESERVOIR_RANGE,
                    step = LOW_RESERVOIR_STEP,
                    onValueChange = viewModel::updateLowReservoirRate
                )
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.ap550_save_user_options))
        }
    }
}

/** On/off setting with the two state names shown as the value. */
@Composable
private fun SwitchRow(
    label: String,
    onText: String,
    offText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            Text(
                text = if (checked) onText else offText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Number setting with the allowed range and step from the pump's own menu.
 *
 * The slider only offers values on the step grid between [range] bounds, and the current value is
 * shown next to the label.
 */
@Composable
private fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    val steps = ((range.last - range.first) / step) - 1
    Column(modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                // Snap to the step grid here as well: the step count only constrains the slider
                // position, not the value it reports.
                val snapped = range.first + ((raw - range.first) / step).roundToInt() * step
                onValueChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps
        )
    }
}

private val LCD_ON_TIME_RANGE = 5..240
private const val LCD_ON_TIME_STEP = 5
private const val BACKLIGHT_ON_TIME_MAX = 60
private val SHUTDOWN_HOUR_RANGE = 0..24
private val LOW_RESERVOIR_RANGE = 10..50
private const val LOW_RESERVOIR_STEP = 10
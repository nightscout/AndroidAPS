package app.aaps.pump.diaconn.compose

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.pump.diaconn.R
import kotlin.math.roundToInt

@Composable
fun DiaconnUserOptionsScreen(
    viewModel: DiaconnUserOptionsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Alarm section
        SectionHeader(stringResource(R.string.diaconn_g8_pumpalarm))
        RadioGroup(
            options = listOf(
                1 to stringResource(R.string.diaconn_g8_pumpalarm_sound),
                2 to stringResource(R.string.diaconn_g8_pumpalarm_vibrate),
                3 to stringResource(R.string.diaconn_g8_pumpalarm_silent)
            ),
            selected = state.beepAndAlarm,
            onSelect = viewModel::updateBeepAndAlarm
        )

        // Alarm intensity (hidden when silent)
        if (state.beepAndAlarm != 3) {
            RadioGroup(
                options = listOf(
                    1 to stringResource(R.string.diaconn_g8_pumpalarm_intensity_low),
                    2 to stringResource(R.string.diaconn_g8_pumpalarm_intensity_middle),
                    3 to stringResource(R.string.diaconn_g8_pumpalarm_intensity_high)
                ),
                selected = state.alarmIntensity,
                onSelect = viewModel::updateAlarmIntensity
            )
        }

        FilledTonalButton(
            onClick = viewModel::saveAlarm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.save))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Screen timeout section
        SectionHeader(stringResource(R.string.diaconn_g8_screentimeout))
        RadioGroup(
            options = listOf(
                1 to stringResource(R.string.diaconn_g8_pumpscreentimeout_10),
                2 to stringResource(R.string.diaconn_g8_pumpscreentimeout_20),
                3 to stringResource(R.string.diaconn_g8_pumpscreentimeout_30)
            ),
            selected = state.lcdOnTimeSec,
            onSelect = viewModel::updateLcdOnTimeSec
        )

        FilledTonalButton(
            onClick = viewModel::saveLcdOnTime,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.save))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Language section
        SectionHeader(stringResource(R.string.diaconn_g8_language))
        RadioGroup(
            options = listOf(
                1 to stringResource(R.string.diaconn_g8_pumplang_chiness),
                2 to stringResource(R.string.diaconn_g8_pumplang_korean),
                3 to stringResource(R.string.diaconn_g8_pumplang_english)
            ),
            selected = state.selectedLanguage,
            onSelect = viewModel::updateSelectedLanguage
        )

        FilledTonalButton(
            onClick = viewModel::saveLanguage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.save))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Bolus speed section
        SectionHeader(stringResource(R.string.diaconn_g8_bolus_speed))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = state.bolusSpeed.toFloat(),
                onValueChange = { viewModel.updateBolusSpeed(it.roundToInt()) },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${state.bolusSpeed} U/min",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        FilledTonalButton(
            onClick = viewModel::saveBolusSpeed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.save))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RadioGroup(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = value == selected,
                        onClick = { onSelect(value) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = value == selected,
                    onClick = null
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

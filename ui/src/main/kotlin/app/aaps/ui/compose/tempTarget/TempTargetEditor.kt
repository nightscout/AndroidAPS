package app.aaps.ui.compose.tempTarget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.NumberInputRow
import java.text.DecimalFormat
import app.aaps.core.keys.R as KeysR

/**
 * Editor component for temp target presets with inline activation fields.
 * Displays preset editing fields (name, target, duration) and activation fields (date/time, notes).
 *
 * @param selectedPreset Currently selected preset (null for custom/new TT)
 * @param editorName Name field value (for custom presets)
 * @param editorTarget Target value in user's current units
 * @param editorDuration Duration in minutes
 * @param eventTime Activation timestamp
 * @param eventTimeChanged Whether user modified the activation time
 * @param notes Activation notes
 * @param showNotesField Whether notes field should be shown
 * @param units Current glucose units
 * @param rh Resource helper
 * @param onNameChange Callback when name changes
 * @param onTargetChange Callback when target changes (in user units)
 * @param onDurationChange Callback when duration changes
 * @param onDateClick Callback when date field is clicked
 * @param onTimeClick Callback when time field is clicked
 * @param onNotesChange Callback when notes change
 * @param modifier Modifier for the editor
 */
@Composable
fun TempTargetEditor(
    selectedPreset: TTPreset?,
    editorName: String,
    editorTarget: Double,
    editorDuration: Int, // Duration in minutes (for display)
    eventTime: Long,
    eventTimeChanged: Boolean,
    notes: String,
    showNotesField: Boolean,
    units: GlucoseUnit,
    rh: ResourceHelper,
    onNameChange: (String) -> Unit,
    onTargetChange: (Double) -> Unit,
    onDurationChange: (Long) -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header
        Text(
            text = stringResource(R.string.preset_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Preset name field (only for custom presets)
        if (selectedPreset?.isDeletable == true) {
            OutlinedTextField(
                value = editorName,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.name_short).removeSuffix(":")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )
        }

        // Target value slider
        val (minTarget, maxTarget, targetStep) = when (units) {
            GlucoseUnit.MGDL -> Triple(
                Constants.MIN_TT_MGDL,
                Constants.MAX_TT_MGDL,
                1.0
            )

            GlucoseUnit.MMOL -> Triple(
                Constants.MIN_TT_MMOL,
                Constants.MAX_TT_MMOL,
                0.1
            )
        }

        NumberInputRow(
            labelResId = R.string.temporary_target,
            value = editorTarget,
            onValueChange = onTargetChange,
            valueRange = minTarget..maxTarget,
            step = targetStep,
            valueFormat = if (units == GlucoseUnit.MGDL) DecimalFormat("0") else DecimalFormat("0.0"),
            unitLabel = units.asText,
            modifier = Modifier.fillMaxWidth()
        )

        // Duration slider
        NumberInputRow(
            labelResId = R.string.duration,
            value = editorDuration.toDouble(),
            onValueChange = { onDurationChange((it * 60000L).toLong()) },
            valueRange = 0.0..Constants.MAX_PROFILE_SWITCH_DURATION,
            step = 5.0,
            controlPoints = listOf(
                0.0 to 0.0,             // 0% slider -> 0h
                0.25 to 6.0 * 60.0,     // 25% slider -> 6h
                0.5 to 24.0 * 60.0,     // 50% slider -> 24h
                0.75 to 48.0 * 60.0,    // 75% slider -> 48h
                1.0 to Constants.MAX_PROFILE_SWITCH_DURATION   // 100% slider -> 168h
            ),
            unitLabelResId = KeysR.string.units_min,
            modifier = Modifier.fillMaxWidth()
        )

        // Separator
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        // Activation section header
        Text(
            text = stringResource(R.string.activation),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Date/Time selection row (matches ProfileActivationScreen pattern)
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
                label = { Text(stringResource(R.string.date)) },
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
                    .clickable { onDateClick() },
                singleLine = true
            )

            // Time field
            OutlinedTextField(
                value = dateUtil.timeString(eventTime),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.time)) },
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
                    .clickable { onTimeClick() },
                singleLine = true
            )
        }

        // Notes field (conditional)
        if (showNotesField) {
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text(stringResource(R.string.notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors()
            )
        }
    }
}

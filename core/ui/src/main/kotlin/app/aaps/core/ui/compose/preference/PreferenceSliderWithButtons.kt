package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.SliderWithButtons
import app.aaps.core.ui.compose.dialogs.ValueInputDialog
import app.aaps.core.ui.compose.formatSliderDisplayValue
import java.text.DecimalFormat

private const val MAX_SLIDER_STEPS = 200.0

/**
 * Preference-row variant of [SliderWithButtons]. Forwards to [SliderWithButtons] when the
 * value range has at most [MAX_SLIDER_STEPS] discrete steps; otherwise renders a single
 * tap-to-edit value row that opens [ValueInputDialog]. Preferences are rarely touched, so
 * above the threshold the slider's drag affordance offers no precision benefit and the
 * step-size +/- buttons would only nudge by a tiny fraction of the range — typing is the
 * only sensible input.
 */
@Composable
fun PreferenceSliderWithButtons(
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double = 0.1,
    showValue: Boolean = false,
    valueFormatResId: Int? = null,
    formatAsInt: Boolean = false,
    valueFormat: DecimalFormat = DecimalFormat("0.0"),
    unitLabel: String = "",
    unitLabelResId: Int = 0,
    dialogLabel: String? = null,
    dialogSummary: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tooManySteps = (valueRange.endInclusive - valueRange.start) / step > MAX_SLIDER_STEPS
    if (!tooManySteps) {
        SliderWithButtons(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            step = step,
            showValue = showValue,
            valueFormatResId = valueFormatResId,
            formatAsInt = formatAsInt,
            valueFormat = valueFormat,
            unitLabel = unitLabel,
            unitLabelResId = unitLabelResId,
            dialogLabel = dialogLabel,
            dialogSummary = dialogSummary,
            enabled = enabled,
            modifier = modifier
        )
        return
    }

    var showDialog by remember { mutableStateOf(false) }

    val resolvedUnitLabel = when {
        unitLabelResId != 0 -> stringResource(unitLabelResId)
        unitLabel.isNotEmpty() -> unitLabel
        else -> ""
    }
    val displayText = if (showValue) formatSliderDisplayValue(
        value = value,
        unitLabelResId = unitLabelResId,
        valueFormatResId = valueFormatResId,
        formatAsInt = formatAsInt,
        valueFormat = valueFormat,
        unitLabel = unitLabel
    ) else ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { showDialog = true } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (showValue) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog && enabled) {
        ValueInputDialog(
            currentValue = value,
            valueRange = valueRange,
            step = step,
            label = dialogLabel,
            summary = dialogSummary,
            unitLabel = resolvedUnitLabel,
            unitLabelResId = unitLabelResId,
            valueFormat = valueFormat,
            onValueConfirm = onValueChange,
            onDismiss = { showDialog = false }
        )
    }
}

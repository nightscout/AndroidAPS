package app.aaps.plugins.automation.compose.elements

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.automation.elements.InputBg
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal M3 stepper + text number input for automation element editors.
 * Label & unit handled by the surrounding layout (see [LabelWithElementRow]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationNumberInput(
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    modifier: Modifier = Modifier,
    format: DecimalFormat = DecimalFormat("0.##"),
    suffix: String? = null,
    enabled: Boolean = true
) {
    var text by remember(value) { mutableStateOf(format.format(value)) }
    LaunchedEffect(value) {
        if (text.parseLocalized() != value) text = format.format(value)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedIconButton(
            onClick = {
                val next = clamp(value - step, range)
                text = format.format(next); onValueChange(next)
            },
            enabled = enabled && value > range.start,
            modifier = Modifier.size(28.dp)
        ) { Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp)) }
        val interactionSource = remember { MutableInteractionSource() }
        val textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                val p = it.parseLocalized()
                if (p != null && p in range) onValueChange(p)
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = textStyle,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 28.dp)
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                interactionSource = interactionSource,
                suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = TextFieldDefaults.shape
                    )
                }
            )
        }
        OutlinedIconButton(
            onClick = {
                val next = clamp(value + step, range)
                text = format.format(next); onValueChange(next)
            },
            enabled = enabled && value < range.endInclusive,
            modifier = Modifier.size(28.dp)
        ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
    }
}

private fun clamp(v: Double, r: ClosedFloatingPointRange<Double>) = min(max(v, r.start), r.endInclusive)

private fun String.parseLocalized(): Double? {
    if (isBlank()) return null
    val n = replace(',', '.')
    return n.toDoubleOrNull() ?: runCatching {
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.getDefault())).parse(this)?.toDouble()
    }.getOrNull()
}

@Composable
fun InputDoubleEditor(
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    format: DecimalFormat = DecimalFormat("0.##"),
    suffix: String? = null,
    modifier: Modifier = Modifier
) = AutomationNumberInput(value, onValueChange, range, step, modifier, format, suffix)

@Composable
fun InputBgEditor(
    bg: InputBg,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val (range, step, format) = when (bg.units) {
        GlucoseUnit.MMOL -> Triple(InputBg.MMOL_MIN..InputBg.MMOL_MAX, 0.1, DecimalFormat("0.0"))
        GlucoseUnit.MGDL -> Triple(InputBg.MGDL_MIN..InputBg.MGDL_MAX, 1.0, DecimalFormat("0"))
    }
    AutomationNumberInput(bg.value, onValueChange, range, step, modifier, format, bg.units.asText)
}

@Composable
fun InputDurationEditor(
    duration: InputDuration,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val (range, step, suffix) = when (duration.unit) {
        InputDuration.TimeUnit.MINUTES -> Triple(5.0..(24 * 60.0), 10.0, "min")
        InputDuration.TimeUnit.HOURS   -> Triple(1.0..24.0, 1.0, "h")
        InputDuration.TimeUnit.DAYS    -> Triple(1.0..30.0, 1.0, "d")
    }
    AutomationNumberInput(
        value = duration.value.toDouble(),
        onValueChange = { onChange(it.toInt()) },
        range = range,
        step = step,
        modifier = modifier,
        format = DecimalFormat("0"),
        suffix = suffix
    )
}

@Composable
fun InputPercentEditor(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) = AutomationNumberInput(value, onValueChange, InputPercent.MIN..InputPercent.MAX, 5.0, modifier, DecimalFormat("0"), "%")

@Composable
fun InputInsulinEditor(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) = AutomationNumberInput(value, onValueChange, -20.0..20.0, 0.1, modifier, DecimalFormat("0.0"), "U")

@Composable
fun InputTempTargetEditor(
    value: Double,
    units: GlucoseUnit,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val (range, step, format) = if (units == GlucoseUnit.MMOL)
        Triple(Constants.MIN_TT_MMOL..Constants.MAX_TT_MMOL, 0.1, DecimalFormat("0.0"))
    else
        Triple(Constants.MIN_TT_MGDL..Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"))
    AutomationNumberInput(value, onValueChange, range, step, modifier, format, units.asText)
}

// ---------- Previews ----------

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewNumberInput() {
    MaterialTheme {
        var v by remember { mutableStateOf(5.5) }
        AutomationNumberInput(
            value = v, onValueChange = { v = it },
            range = 0.0..10.0, step = 0.1,
            format = DecimalFormat("0.0"), suffix = "mmol/L"
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PreviewPercent() {
    MaterialTheme {
        var v by remember { mutableStateOf(100.0) }
        InputPercentEditor(value = v, onValueChange = { v = it })
    }
}

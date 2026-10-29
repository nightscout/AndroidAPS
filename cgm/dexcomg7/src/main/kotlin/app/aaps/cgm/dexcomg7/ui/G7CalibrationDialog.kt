package app.aaps.cgm.dexcomg7.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.aaps.cgm.dexcomg7.R
import app.aaps.core.ui.compose.AapsSpacing

/**
 * Asks for a meter value. The sensor is factory calibrated, so this is for when readings are clearly
 * off; the guidance text says so. The value is in the user's units.
 */
@Composable
fun G7CalibrationDialog(isMmol: Boolean, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.replace(',', '.').toDoubleOrNull()
    // What the sensor can measure: 40-400 mg/dL, 2.2-22.2 mmol/L.
    val valid = value != null && if (isMmol) value in 2.2..22.2 else value in 40.0..400.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dexcom_g7_calibrate)) },
        text = {
            Column {
                Text(stringResource(R.string.dexcom_g7_calibration_guidance), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(AapsSpacing.large))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                    label = { Text(stringResource(R.string.dexcom_g7_calibration_value, if (isMmol) "mmol/L" else "mg/dL")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = valid) { Text(stringResource(app.aaps.core.ui.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
        }
    )
}

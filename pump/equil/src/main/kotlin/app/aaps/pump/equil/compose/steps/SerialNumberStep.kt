package app.aaps.pump.equil.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.EquilWizardViewModel

private val HEX_6_PATTERN = Regex("^[A-F0-9]{6}$")
private val HEX_4_PATTERN = Regex("^[A-F0-9]{4}$")

@Composable
internal fun SerialNumberStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()

    SerialNumberStepContent(
        scanning = scanning,
        scanError = scanError,
        onStartScan = { serial, password ->
            viewModel.prepareBLEScan()
            viewModel.startBLEScan(serial, password)
        },
        onCancel = onCancel
    )
}

@Composable
private fun SerialNumberStepContent(
    scanning: Boolean,
    scanError: String?,
    onStartScan: (serial: String, password: String) -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    var serial by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val serialErrorText = stringResource(R.string.error_mustbe6hexadidits)
    val passwordErrorText = stringResource(app.aaps.core.ui.R.string.error_mustbe4hexadidits)

    val isSerialValid = HEX_6_PATTERN.matches(serial)
    val isPasswordValid = password.isEmpty() || HEX_4_PATTERN.matches(password)

    WizardStepLayout(
        modifier = Modifier.clearFocusOnTap(focusManager),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.equil_devices_number),
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = serial,
            onValueChange = { serial = it.uppercase().take(6) },
            label = { Text(stringResource(R.string.equil_devices_number_hint)) },
            isError = serial.isNotEmpty() && !isSerialValid,
            supportingText = if (serial.isNotEmpty() && !isSerialValid) {
                { Text(serialErrorText) }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it.uppercase().take(4) },
            label = { Text(stringResource(R.string.equil_set_pair_password)) },
            isError = password.isNotEmpty() && !HEX_4_PATTERN.matches(password),
            supportingText = if (password.isNotEmpty() && !HEX_4_PATTERN.matches(password)) {
                { Text(passwordErrorText) }
            } else null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.equil_password_tips),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AapsSpacing.medium))

        Button(
            onClick = {
                if (isSerialValid && isPasswordValid) {
                    onStartScan(serial, password)
                }
            },
            enabled = !scanning && isSerialValid && isPasswordValid,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (scanError != null) stringResource(app.aaps.core.ui.R.string.retry)
                    else stringResource(R.string.equil_pair)
                )
            }
        }

        if (scanError != null) {
            WizardErrorBanner(message = scanError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SerialNumberStepPreview() {
    SerialNumberStepContent(
        scanning = false,
        scanError = null,
        onStartScan = { _, _ -> },
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun SerialNumberStepScanningPreview() {
    SerialNumberStepContent(
        scanning = true,
        scanError = null,
        onStartScan = { _, _ -> },
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun SerialNumberStepErrorPreview() {
    SerialNumberStepContent(
        scanning = false,
        scanError = "Device not found. Please retry.",
        onStartScan = { _, _ -> },
        onCancel = {}
    )
}

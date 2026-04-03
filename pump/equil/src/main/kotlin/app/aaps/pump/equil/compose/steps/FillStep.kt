package app.aaps.pump.equil.compose.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.EquilWizardViewModel

@Composable
internal fun FillStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val autoFilling by viewModel.autoFilling.collectAsStateWithLifecycle()
    val fillComplete by viewModel.fillComplete.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    FillStepContent(
        isLoading = isLoading,
        autoFilling = autoFilling,
        fillComplete = fillComplete,
        errorMessage = errorMessage,
        onStartFill = { viewModel.startFill() },
        onStopFill = { viewModel.stopFill() },
        onNext = { viewModel.finishFill() },
        onCancel = onCancel
    )
}

@Composable
private fun FillStepContent(
    isLoading: Boolean,
    autoFilling: Boolean,
    fillComplete: Boolean,
    errorMessage: String?,
    onStartFill: () -> Unit,
    onStopFill: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = if (fillComplete) WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNext
        ) else null,
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.equil_title_fill),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.equil_insert),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.equil_insert2),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(AapsSpacing.medium))

        if (!fillComplete) {
            if (autoFilling) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AapsSpacing.xxLarge),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.equil_dressing_onkey_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(AapsSpacing.medium))
                OutlinedButton(onClick = onStopFill) {
                    Text(stringResource(R.string.equil_stop))
                }
            } else {
                Button(
                    onClick = onStartFill,
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.equil_fill))
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(AapsSpacing.medium))
            WizardErrorBanner(message = errorMessage)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FillStepIdlePreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = false,
        fillComplete = false,
        errorMessage = null,
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun FillStepFillingPreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = true,
        fillComplete = false,
        errorMessage = null,
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun FillStepCompletePreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = false,
        fillComplete = true,
        errorMessage = null,
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun FillStepErrorPreview() {
    FillStepContent(
        isLoading = false,
        autoFilling = false,
        fillComplete = false,
        errorMessage = "Replace reservoir",
        onStartFill = {},
        onStopFill = {},
        onNext = {},
        onCancel = {}
    )
}

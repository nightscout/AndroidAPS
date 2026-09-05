package app.aaps.pump.equil.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.ErrorBanner
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.EquilWizardViewModel

@Composable
internal fun UnpairConfirmStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val unpairResult by viewModel.unpairResultMessage.collectAsStateWithLifecycle()
    val serialNumber by viewModel.serialNumberDisplay.collectAsStateWithLifecycle()

    UnpairConfirmStepContent(
        isLoading = isLoading,
        errorMessage = errorMessage,
        unpairResult = unpairResult,
        serialNumber = serialNumber,
        onConfirm = { viewModel.confirmUnpair() },
        onDismissResult = { viewModel.dismissUnpairResult() },
        onCancel = onCancel
    )
}

/**
 * @see UnpairConfirmStepPreview
 * @see UnpairConfirmStepLoadingPreview
 * @see UnpairConfirmStepResultPreview
 * @see UnpairConfirmStepErrorPreview
 */
@Composable
internal fun UnpairConfirmStepContent(
    isLoading: Boolean,
    errorMessage: String?,
    unpairResult: String?,
    serialNumber: String,
    onConfirm: () -> Unit,
    onDismissResult: () -> Unit,
    onCancel: () -> Unit
) {
    if (unpairResult != null) {
        WizardStepLayout(
            primaryButton = WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.finish),
                onClick = onDismissResult
            )
        ) {
            Text(
                text = unpairResult,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        WizardStepLayout(
            primaryButton = WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.finish),
                onClick = onConfirm,
                loading = isLoading
            ),
            secondaryButton = WizardButton(
                text = stringResource(app.aaps.core.ui.R.string.cancel),
                onClick = onCancel
            )
        ) {
            Text(
                text = stringResource(R.string.equil_unpair_alert, serialNumber),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(AapsSpacing.medium))
            Text(
                text = stringResource(R.string.equil_unbind_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(AapsSpacing.medium))
                ErrorBanner(message = errorMessage)
            }
        }
    }
}

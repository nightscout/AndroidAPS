package app.aaps.pump.equil.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.EquilWizardViewModel

@Composable
internal fun AirStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val airRemovalDone by viewModel.airRemovalDone.collectAsStateWithLifecycle()

    AirStepContent(
        isLoading = isLoading,
        errorMessage = errorMessage,
        airRemovalDone = airRemovalDone,
        onRemoveAir = { viewModel.startAirRemoval() },
        onFinish = { viewModel.finishAirStep() },
        onCancel = onCancel
    )
}

@Composable
private fun AirStepContent(
    isLoading: Boolean,
    errorMessage: String?,
    airRemovalDone: Boolean,
    onRemoveAir: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onFinish,
            loading = isLoading,
            enabled = airRemovalDone && !isLoading
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.equil_hint2),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.equil_hint1),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(AapsSpacing.medium))

        Button(
            onClick = onRemoveAir,
            enabled = !isLoading
        ) {
            Text(stringResource(R.string.equil_air))
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(AapsSpacing.medium))
            WizardErrorBanner(message = errorMessage)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AirStepPreview() {
    AirStepContent(
        isLoading = false,
        errorMessage = null,
        airRemovalDone = false,
        onRemoveAir = {},
        onFinish = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun AirStepDonePreview() {
    AirStepContent(
        isLoading = false,
        errorMessage = null,
        airRemovalDone = true,
        onRemoveAir = {},
        onFinish = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun AirStepErrorPreview() {
    AirStepContent(
        isLoading = false,
        errorMessage = "Communication error",
        airRemovalDone = false,
        onRemoveAir = {},
        onFinish = {},
        onCancel = {}
    )
}

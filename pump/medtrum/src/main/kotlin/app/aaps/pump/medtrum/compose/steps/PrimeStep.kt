package app.aaps.pump.medtrum.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel
import app.aaps.pump.medtrum.compose.WizardButton
import app.aaps.pump.medtrum.compose.WizardStepLayout

private const val PRIME_MAX_PROGRESS = 150f

@Composable
fun PrimeStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    val primeProgress by viewModel.medtrumPump.primeProgressFlow.collectAsStateWithLifecycle()

    val isPriming = patchStep == PatchStep.PRIMING
    val isPrimeComplete = patchStep == PatchStep.PRIME_COMPLETE
    val isError = setupStep == MedtrumPatchViewModel.SetupStep.ERROR

    // Trigger startPrime when entering PRIMING step
    LaunchedEffect(patchStep) {
        if (patchStep == PatchStep.PRIMING) {
            viewModel.startPrime()
        }
    }

    // Auto-navigate on primed
    LaunchedEffect(setupStep) {
        if (setupStep == MedtrumPatchViewModel.SetupStep.PRIMED && patchStep == PatchStep.PRIMING) {
            viewModel.moveStep(PatchStep.PRIME_COMPLETE)
        }
    }

    WizardStepLayout(
        primaryButton = when {
            patchStep == PatchStep.PRIME -> WizardButton(
                text = stringResource(R.string.next),
                onClick = { viewModel.moveStep(PatchStep.PRIMING) }
            )

            isPriming && !isError        -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            isError                      -> WizardButton(
                text = stringResource(R.string.retry),
                onClick = {
                    viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.FILLED)
                    viewModel.moveStep(PatchStep.PRIMING)
                }
            )

            isPrimeComplete              -> WizardButton(
                text = stringResource(R.string.next),
                onClick = { viewModel.moveStep(PatchStep.ATTACH_PATCH) }
            )

            else                         -> null
        },
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        when {
            patchStep == PatchStep.PRIME -> {
                Text(
                    text = stringResource(R.string.half_press_needle).stripHtml(),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.do_not_attach_to_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            isPriming && !isError        -> {
                Text(
                    text = stringResource(R.string.wait_for_priming),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (primeProgress / PRIME_MAX_PROGRESS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.do_not_attach_to_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            isError                      -> {
                Text(
                    text = stringResource(R.string.priming_error).stripHtml(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.do_not_attach_to_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            isPrimeComplete              -> {
                Text(
                    text = stringResource(R.string.press_next).stripHtml(),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.do_not_attach_to_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

private const val PRIME_MAX_PROGRESS = 150f

@Composable
fun PrimeStep(
    viewModel: MedtrumPatchViewModel,
    onCancel: () -> Unit
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val setupStep by viewModel.setupStep.collectAsStateWithLifecycle()
    val primeProgress by viewModel.medtrumPump.primeProgressFlow.collectAsStateWithLifecycle()

    val isPrime = patchStep == PatchStep.PRIME
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

    PrimeStepContent(
        isPrime = isPrime,
        isPriming = isPriming,
        isPrimeComplete = isPrimeComplete,
        isError = isError,
        primeProgress = primeProgress,
        onStartPrime = { viewModel.moveStep(PatchStep.PRIMING) },
        onRetry = {
            viewModel.updateSetupStep(MedtrumPatchViewModel.SetupStep.FILLED)
            viewModel.moveStep(PatchStep.PRIMING)
        },
        onNext = { viewModel.moveStep(PatchStep.ATTACH_PATCH) },
        onCancel = onCancel
    )
}

@Composable
private fun PrimeStepContent(
    isPrime: Boolean,
    isPriming: Boolean,
    isPrimeComplete: Boolean,
    isError: Boolean,
    primeProgress: Int,
    onStartPrime: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = when {
            isPrime                  -> WizardButton(
                text = stringResource(R.string.next),
                onClick = onStartPrime
            )

            isPriming && !isError    -> WizardButton(
                text = stringResource(R.string.next),
                onClick = {},
                loading = true
            )

            isError                  -> WizardButton(
                text = stringResource(R.string.retry),
                onClick = onRetry
            )

            isPrimeComplete          -> WizardButton(
                text = stringResource(R.string.next),
                onClick = onNext
            )

            else                     -> null
        },
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        when {
            isPrime              -> {
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

            isPriming && !isError -> {
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

            isError               -> {
                WizardErrorBanner(message = stringResource(R.string.priming_error).stripHtml())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.do_not_attach_to_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            isPrimeComplete       -> {
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

@Preview(showBackground = true)
@Composable
private fun PrimeStepInitialPreview() {
    PrimeStepContent(
        isPrime = true,
        isPriming = false,
        isPrimeComplete = false,
        isError = false,
        primeProgress = 0,
        onStartPrime = {},
        onRetry = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PrimeStepPrimingPreview() {
    PrimeStepContent(
        isPrime = false,
        isPriming = true,
        isPrimeComplete = false,
        isError = false,
        primeProgress = 75,
        onStartPrime = {},
        onRetry = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PrimeStepErrorPreview() {
    PrimeStepContent(
        isPrime = false,
        isPriming = false,
        isPrimeComplete = false,
        isError = true,
        primeProgress = 0,
        onStartPrime = {},
        onRetry = {},
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun PrimeStepCompletePreview() {
    PrimeStepContent(
        isPrime = false,
        isPriming = false,
        isPrimeComplete = true,
        isError = false,
        primeProgress = 150,
        onStartPrime = {},
        onRetry = {},
        onNext = {},
        onCancel = {}
    )
}

private fun String.stripHtml(): String = this.replace(Regex("<[^>]*>"), "")

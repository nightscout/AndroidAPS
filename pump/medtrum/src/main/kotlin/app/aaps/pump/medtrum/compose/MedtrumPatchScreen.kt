package app.aaps.pump.medtrum.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.StepProgressIndicator
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.code.PatchStep
import app.aaps.pump.medtrum.compose.steps.ActivateStep
import app.aaps.pump.medtrum.compose.steps.AttachStep
import app.aaps.pump.medtrum.compose.steps.CompleteStep
import app.aaps.pump.medtrum.compose.steps.ConfirmDeactivateStep
import app.aaps.pump.medtrum.compose.steps.DeactivateCompleteStep
import app.aaps.pump.medtrum.compose.steps.DeactivatingStep
import app.aaps.pump.medtrum.compose.steps.PrepareStep
import app.aaps.pump.medtrum.compose.steps.PrimeStep
import app.aaps.pump.medtrum.compose.steps.RetryActivationStep
import app.aaps.pump.medtrum.compose.steps.SelectInsulinStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedtrumPatchScreen(
    viewModel: MedtrumPatchViewModel
) {
    val patchStep by viewModel.patchStep.collectAsStateWithLifecycle()
    val titleResId by viewModel.title.collectAsStateWithLifecycle()
    val totalSteps by viewModel.totalSteps.collectAsStateWithLifecycle()
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()

    var showCancelDialog by remember { mutableStateOf(false) }

    // Back handler
    val canGoBack = patchStep in listOf(
        PatchStep.PREPARE_PATCH,
        PatchStep.SELECT_INSULIN,
        PatchStep.START_DEACTIVATION,
        PatchStep.RETRY_ACTIVATION,
        PatchStep.COMPLETE,
        PatchStep.DEACTIVATION_COMPLETE
    )

    BackHandler(enabled = true) {
        when (patchStep) {
            PatchStep.PREPARE_PATCH,
            PatchStep.SELECT_INSULIN,
            PatchStep.START_DEACTIVATION,
            PatchStep.RETRY_ACTIVATION      -> {
                viewModel.handleCancel()
            }

            PatchStep.COMPLETE,
            PatchStep.DEACTIVATION_COMPLETE -> {
                viewModel.handleComplete()
            }

            else                            -> {
                // Block back during critical operations
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.change_patch_label)) },
            text = { Text(stringResource(R.string.cancel_sure)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.moveStep(PatchStep.CANCEL)
                }) {
                    Text(stringResource(app.aaps.core.ui.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(app.aaps.core.ui.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleResId)) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = {
                            when (patchStep) {
                                PatchStep.PREPARE_PATCH,
                                PatchStep.SELECT_INSULIN,
                                PatchStep.START_DEACTIVATION,
                                PatchStep.RETRY_ACTIVATION      -> viewModel.handleCancel()

                                PatchStep.COMPLETE,
                                PatchStep.DEACTIVATION_COMPLETE -> viewModel.handleComplete()

                                else                            -> {}
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Step progress indicator
            if (patchStep != null) {
                StepProgressIndicator(
                    totalSteps = totalSteps,
                    currentStep = currentStepIndex
                )
            }

            // Step content with transitions
            AnimatedContent(
                targetState = patchStep,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 4 })
                        .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
                },
                label = "patchStepTransition"
            ) { step ->
                val onCancel: () -> Unit = { showCancelDialog = true }

                when (step) {
                    PatchStep.PREPARE_PATCH,
                    PatchStep.PREPARE_PATCH_CONNECT    -> PrepareStep(viewModel, onCancel)

                    PatchStep.SELECT_INSULIN           -> SelectInsulinStep(viewModel, onCancel)

                    PatchStep.PRIME,
                    PatchStep.PRIMING,
                    PatchStep.PRIME_COMPLETE           -> PrimeStep(viewModel, onCancel)

                    PatchStep.ATTACH_PATCH             -> AttachStep(viewModel, onCancel)

                    PatchStep.ACTIVATE,
                    PatchStep.ACTIVATE_COMPLETE        -> ActivateStep(viewModel, onCancel)

                    PatchStep.COMPLETE,
                    PatchStep.CANCEL                   -> CompleteStep(viewModel)

                    PatchStep.START_DEACTIVATION       -> ConfirmDeactivateStep(viewModel, onCancel)

                    PatchStep.DEACTIVATE,
                    PatchStep.FORCE_DEACTIVATION       -> DeactivatingStep(viewModel, onCancel)

                    PatchStep.DEACTIVATION_COMPLETE    -> DeactivateCompleteStep(viewModel)

                    PatchStep.RETRY_ACTIVATION,
                    PatchStep.RETRY_ACTIVATION_CONNECT -> RetryActivationStep(viewModel, onCancel)

                    null                               -> {} // Initial state before step is set
                }
            }
        }
    }
}

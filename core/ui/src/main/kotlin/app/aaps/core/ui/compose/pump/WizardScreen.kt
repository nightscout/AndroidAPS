package app.aaps.core.ui.compose.pump

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.dialogs.OkCancelDialog

/**
 * Shared wizard screen shell for pump activation/deactivation workflows.
 *
 * Provides: StepProgressIndicator + AnimatedContent transitions
 * + cancel confirmation dialog + BackHandler.
 *
 * Does NOT include a Scaffold or TopAppBar — the parent toolbar
 * (from the host activity) should be configured via setToolbarConfig
 * in the pump's ComposeContent.
 *
 * @param S Step type (typically an enum)
 * @param currentStep Current wizard step (null hides content)
 * @param totalSteps Total number of steps for progress indicator
 * @param currentStepIndex Zero-based current step position
 * @param canGoBack Whether back navigation is allowed on current step
 * @param onBack Called when back/arrow pressed on allowed steps
 * @param cancelDialogTitle Title for the cancel confirmation dialog
 * @param cancelDialogText Body text for the cancel confirmation dialog
 * @param stepContent Composable content for the current step
 */
@Composable
fun <S> WizardScreen(
    currentStep: S?,
    totalSteps: Int,
    currentStepIndex: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    cancelDialogTitle: String,
    cancelDialogText: String,
    stepContent: @Composable (step: S, onCancel: () -> Unit) -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    // System back button: if can go back, go to previous step; otherwise show cancel dialog
    BackHandler(enabled = true) {
        if (canGoBack) {
            onBack()
        } else {
            showCancelDialog = true
        }
    }

    if (showCancelDialog) {
        OkCancelDialog(
            title = cancelDialogTitle,
            message = cancelDialogText,
            onConfirm = {
                showCancelDialog = false
                onBack()
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (currentStep != null) {
            StepProgressIndicator(
                totalSteps = totalSteps,
                currentStep = currentStepIndex
            )
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 4 })
                    .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
            },
            label = "wizardStepTransition"
        ) { step ->
            if (step != null) {
                stepContent(step) { showCancelDialog = true }
            }
        }
    }
}

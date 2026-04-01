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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog

/**
 * Shared wizard screen shell for pump activation/deactivation workflows.
 *
 * Provides: StepProgressIndicator + AnimatedContent transitions
 * + cancel confirmation dialog + BackHandler + toolbar management.
 *
 * When [setToolbarConfig] is provided, the wizard takes over the parent toolbar:
 * - Sets the title to [title]
 * - Removes the navigation icon (back arrow) to prevent unconfirmed exit
 * - System back button shows a cancel confirmation dialog instead
 *
 * @param S Step type (typically an enum)
 * @param currentStep Current wizard step (null hides content)
 * @param totalSteps Total number of steps for progress indicator
 * @param currentStepIndex Zero-based current step position
 * @param canGoBack Whether back navigation is allowed on current step
 * @param onBack Called when user confirms exit from the wizard
 * @param cancelDialogTitle Title for the cancel confirmation dialog
 * @param cancelDialogText Body text for the cancel confirmation dialog
 * @param title Toolbar title shown during the wizard
 * @param setToolbarConfig Callback to configure the parent toolbar (hides back arrow)
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
    title: String = "",
    setToolbarConfig: ((ToolbarConfig) -> Unit)? = null,
    stepContent: @Composable (step: S, onCancel: () -> Unit) -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    // Take over toolbar: back arrow shows confirmation dialog
    if (setToolbarConfig != null) {
        // Use a stable callback ref so toolbar icon can trigger dialog
        val onRequestCancel = remember { { showCancelDialog = true } }
        DisposableEffect(title, onRequestCancel) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onRequestCancel) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {}
                )
            )
            onDispose {
                // Clear toolbar when wizard leaves composition
                setToolbarConfig(ToolbarConfig(title = "", navigationIcon = {}, actions = {}))
            }
        }
    }

    // System back button: always confirm before leaving the wizard
    BackHandler(enabled = true) {
        showCancelDialog = true
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

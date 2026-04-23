package app.aaps.ui.compose.scenes.wizard

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.pump.StepProgressIndicator

@Composable
fun SceneWizardScreen(
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SceneWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.scene)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Step progress indicator
            if (state.currentStep >= SceneWizardViewModel.STEP_PROFILE) {
                val editOffset = if (viewModel.isEditMode) SceneWizardViewModel.STEP_PROFILE else SceneWizardViewModel.STEP_INFO
                val totalSteps = SceneWizardViewModel.TOTAL_STEPS - editOffset
                StepProgressIndicator(
                    totalSteps = totalSteps,
                    currentStep = state.currentStep - editOffset, // 0-indexed for the shared indicator
                    modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge)
                )
            } else if (state.currentStep == SceneWizardViewModel.STEP_INFO) {
                StepProgressIndicator(
                    totalSteps = SceneWizardViewModel.TOTAL_STEPS,
                    currentStep = 0,
                    modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge)
                )
            }

            // Step content with animation
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it / 4 } + fadeIn() togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it / 4 } + fadeOut()
                    }
                },
                label = "wizard_step",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    SceneWizardViewModel.STEP_TEMPLATE   -> TemplatePickerStep(onSelect = viewModel::selectTemplate)
                    SceneWizardViewModel.STEP_INFO        -> InfoStep(state, viewModel::back, viewModel::next)
                    SceneWizardViewModel.STEP_PROFILE     -> ProfileStep(
                        state = state, onToggle = viewModel::setProfileEnabled,
                        onUpdate = viewModel::updateProfileAction, profileNames = viewModel.profileNames,
                        onBack = viewModel::back, onNext = viewModel::next
                    )

                    SceneWizardViewModel.STEP_TEMP_TARGET -> TempTargetStep(
                        state = state, onToggle = viewModel::setTtEnabled,
                        onUpdate = viewModel::updateTtAction, ttPresets = viewModel.ttPresets,
                        formatBgWithUnits = viewModel::formatBgWithUnits,
                        onBack = viewModel::back, onNext = viewModel::next
                    )

                    SceneWizardViewModel.STEP_SMB         -> SmbStep(
                        state = state, onToggle = viewModel::setSmbEnabled,
                        onUpdate = viewModel::updateSmbAction,
                        onBack = viewModel::back, onNext = viewModel::next
                    )

                    SceneWizardViewModel.STEP_LOOP_MODE   -> LoopModeStep(
                        state = state, onToggle = viewModel::setLoopModeEnabled,
                        onUpdate = viewModel::updateLoopModeAction,
                        onBack = viewModel::back, onNext = viewModel::next
                    )

                    SceneWizardViewModel.STEP_CAREPORTAL  -> CarePortalStep(
                        state = state, onToggle = viewModel::setCarePortalEnabled,
                        onUpdate = viewModel::updateCarePortalAction,
                        translateEventType = viewModel::translateEventType,
                        onBack = viewModel::back, onNext = viewModel::next
                    )

                    SceneWizardViewModel.STEP_DURATION    -> DurationStep(
                        state = state, onSetDuration = viewModel::setDuration,
                        onBack = viewModel::back, onNext = viewModel::next
                    )

                    SceneWizardViewModel.STEP_NAME_ICON   -> NameIconStep(
                        state = state, onSetName = viewModel::setName,
                        onSetIcon = viewModel::setIcon,
                        onBack = viewModel::back, onFinish = { if (viewModel.save()) onFinished() }
                    )
                }
            }
        }
    }
}

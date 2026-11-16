package app.aaps.pump.aaruy.compose

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.aaruy.R

@Composable
fun AaruyReplaceWizardScreen(
    viewModel: AaruyReplaceWizardViewModel,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var nextStepDialog by remember { mutableStateOf(false) }

    if (nextStepDialog) {
        OkCancelDialog(
            title = stringResource(R.string.notifyTitle),
            message = stringResource(R.string.actions_reservoir_prime_confirm),
            onConfirm = {
                viewModel.changeUiStatePage(AaruyReplaceWizardStep.REPLACE_STEP_CONNECT_BODY)
                nextStepDialog = false
            },
            onDismiss = {
                nextStepDialog = false
            }
        )
    }

    MyLoadingContainer(
        show = viewModel.showLoading.value,
        ifShowText = viewModel.showOverlay.value,
        showText = viewModel.showOverlayText.value,
        showTipText = viewModel.showOverlayTipText.value
    ) {
        WizardScreen(
            currentStep = state.step,
            totalSteps = AaruyReplaceWizardStep.entries.size,
            currentStepIndex = state.step.ordinal,
            canGoBack = true,
            onBack = onCancel,
            cancelDialogTitle = stringResource(R.string.aaruy_pairing),
            cancelDialogText = stringResource(app.aaps.core.ui.R.string.cancel)
        ) { step, wizardCancel ->
            when (step) {
                AaruyReplaceWizardStep.REPLACE_STEP_PUSH_BACK    -> {
                    AaruyReplacePushBackPage(
                        viewModel = viewModel,
                        onFinish = onFinish,
                    ) {
                        viewModel.changeUiStatePage(AaruyReplaceWizardStep.REPLACE_STEP_POSITION)
                    }
                }

                AaruyReplaceWizardStep.REPLACE_STEP_POSITION     -> {
                    AaruyReplacePositionPage(
                        viewModel = viewModel
                    ) {
                        nextStepDialog = true
                    }
                }

                AaruyReplaceWizardStep.REPLACE_STEP_CONNECT_BODY -> {
                    AaruyReplaceConnectBodyPage(
                        viewModel = viewModel
                    ) {
                        viewModel.changeUiStatePage(AaruyReplaceWizardStep.REPLACE_STEP_FILLING)
                    }
                }

                AaruyReplaceWizardStep.REPLACE_STEP_FILLING      -> {
                    AaruyReplaceFillingPage(
                        viewModel = viewModel
                    ) {
                        viewModel.startBaseInject()
                    }
                }
            }
        }
    }
}

@Composable
private fun AaruyReplacePushBackPage(
    viewModel: AaruyReplaceWizardViewModel,
    onFinish: () -> Unit,
    onNext: () -> Unit,
) {
    var showPushBackDialog by remember { mutableStateOf(false) }
    var showPumpNoLinkDialog by remember { mutableStateOf(false) }

    if (showPushBackDialog) {
        OkCancelDialog(
            title = stringResource(R.string.replace_bat_rescv_label),
            message = stringResource(R.string.actions_reservoir_rewind_prompt),
            onConfirm = {
                viewModel.replaceDevice()
                showPushBackDialog = false
            },
            onDismiss = {
                showPushBackDialog = false
                onFinish.invoke()
            }
        )
    }

    if (showPumpNoLinkDialog) {
        OkDialog(
            title = stringResource(R.string.notifyTitle),
            message = stringResource(R.string.bluetooth_disconnect),
            onDismiss = {
                showPumpNoLinkDialog = false
                onFinish.invoke()
            }
        )
    }

    LaunchedEffect(Unit) {
        if (viewModel.isBleConnect.value) {
            showPushBackDialog = true
        } else {
            showPumpNoLinkDialog = true
        }
    }

    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.setting_next),
            onClick = onNext
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.actions_reservoir_prime_before),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (viewModel.isRewinding.value) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.is_backing),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

    }
}

@Composable
private fun AaruyReplacePositionPage(
    viewModel: AaruyReplaceWizardViewModel,
    onNext: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Text(
                text = stringResource(R.string.actions_reservoir_prime_comment),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.actions_reservoir_prime_comment_note),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
                .align(Alignment.BottomCenter)
        ) {
            MyTwoButtonNew(
                modifier1 = Modifier
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                if (viewModel.currentPointerId.intValue == -1 && viewModel.isBleConnect.value) {
                                    viewModel.currentPointerId.intValue = 0
                                    viewModel.isPressLocate.value = true
                                    viewModel.isLocateFinish.value = true
                                    viewModel.pushForward()
                                    true // 消费事件
                                }
                                else {
                                    false
                                }
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            MotionEvent.ACTION_OUTSIDE,
                            MotionEvent.ACTION_POINTER_UP,
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                viewModel.currentPointerId.intValue = -1
                                if (viewModel.isPressLocate.value) {
                                    viewModel.isPressLocate.value = false
                                    viewModel.isLocateFinish.value = false
                                    viewModel.showOverlay.value = false
                                    viewModel.showOverlayText.value = ""
                                    viewModel.showOverlayTipText.value = ""
                                }
                                true // 消费事件
                            }

                            else -> {
                                false
                            }
                        }
                    },
                button1Enable = viewModel.isBleConnect.value,
                onClick1 = {
                    viewModel.pushForward()
                },
                onclick2 = {
                    onNext.invoke()
                },
                TextId1 = R.string.actions_reservoir_prime,
                TextId2 = R.string.setting_next,
                button2Enable = viewModel.isBleConnect.value && viewModel.isPushLocateReply.value
            )
        }
    }
}

@Composable
private fun AaruyReplaceConnectBodyPage(
    viewModel: AaruyReplaceWizardViewModel,
    onNext: () -> Unit,
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.setting_next),
            onClick = onNext
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.actions_reservoir_prime_cannula_before),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

    }
}

@Composable
private fun NeedleChooseShow(
    viewModel: AaruyReplaceWizardViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    viewModel.is6mm.value = true
                    viewModel.sendNeedleType()
                }
                .weight(1.0f)
                .background(
                    color = if (viewModel.is6mm.value) Color(0XFF0095AD) else Color(0XFF222C2E),
                    shape = RoundedCornerShape(size = 4.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = stringResource(R.string.soft_needle_6),
                fontSize = 16.sp,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.width(40.dp))
        Column(
            Modifier
                .clickable {
                    viewModel.is6mm.value = false
                    viewModel.sendNeedleType()
                }
                .weight(1.0f)
                .background(
                    color = if (viewModel.is6mm.value) Color(0XFF222C2E) else Color(0XFF0095AD),
                    shape = RoundedCornerShape(size = 4.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = stringResource(R.string.soft_needle_9),
                fontSize = 16.sp,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AaruyReplaceFillingPage(
    viewModel: AaruyReplaceWizardViewModel,
    onNext: () -> Unit,
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(R.string.actions_reservoir_prime_cannula_action),
            onClick = {
                viewModel.needlePushAir()
            }
        ),
        secondaryButton = WizardButton(
            text = stringResource(R.string.actions_reservoir_cannula_skip),
            onClick = onNext
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.actions_reservoir_prime_cannula),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.actions_reservoir_prime_cannula_note),
                    style = MaterialTheme.typography.headlineSmall
                )
                NeedleChooseShow(viewModel)
            }
        }

    }
}
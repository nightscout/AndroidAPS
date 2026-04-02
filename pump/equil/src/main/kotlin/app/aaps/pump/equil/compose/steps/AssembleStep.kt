package app.aaps.pump.equil.compose.steps

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.EquilUiConstants
import app.aaps.pump.equil.compose.EquilWizardViewModel
import app.aaps.pump.equil.compose.EquilWorkflow
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@Composable
internal fun AssembleStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val workflow by viewModel.workflow.collectAsStateWithLifecycle()
    val isDressing = workflow == EquilWorkflow.CHANGE_INSULIN

    AssembleStepContent(
        isDressing = isDressing,
        imageRes = R.drawable.equil_animation_wizard_assemble,
        onNext = { viewModel.moveStep(viewModel.getAssembleNextStep()) },
        onCancel = onCancel
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AssembleStepContent(
    isDressing: Boolean,
    @DrawableRes imageRes: Int,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNext
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = if (isDressing) stringResource(R.string.equil_title_dressing)
            else stringResource(R.string.equil_ble_config_confirmation_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.equil_assemble),
            style = MaterialTheme.typography.bodyLarge
        )
        GlideImage(
            model = imageRes,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = EquilUiConstants.GIF_MAX_HEIGHT),
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssembleStepPairPreview() {
    AssembleStepContent(
        isDressing = false,
        imageRes = R.drawable.equil_animation_wizard_assemble,
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun AssembleStepDressingPreview() {
    AssembleStepContent(
        isDressing = true,
        imageRes = R.drawable.equil_animation_wizard_assemble,
        onNext = {},
        onCancel = {}
    )
}

package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel

@Composable
internal fun CarelevoPatchFlowStep04Attach(
    viewModel: CarelevoPatchConnectionFlowViewModel
) {
    CarelevoPatchFlowStep04AttachContent(
        onReadyClick = { viewModel.setPage(CarelevoPatchStep.NEEDLE_INSERTION) }
    )
}

@Composable
private fun CarelevoPatchFlowStep04AttachContent(
    onReadyClick: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiStrings.next),
            onClick = onReadyClick
        )
    ) {
        CarelevoPatchAttachSection(
            stepLabel = stringResource(CarelevoStrings.carelevo_patch_step_1),
            title = stringResource(CarelevoStrings.carelevo_patch_attach_step1_title),
            description = stringResource(CarelevoStrings.carelevo_patch_attach_step1_desc)
        )
        CarelevoPatchAttachSection(
            stepLabel = stringResource(CarelevoStrings.carelevo_patch_step_2),
            title = stringResource(CarelevoStrings.carelevo_patch_attach_step2_title),
            description = stringResource(CarelevoStrings.carelevo_patch_attach_step2_desc)
        )
        CarelevoPatchAttachSection(
            stepLabel = stringResource(CarelevoStrings.carelevo_patch_step_3),
            title = stringResource(CarelevoStrings.carelevo_patch_attach_step3_title),
            description = stringResource(CarelevoStrings.carelevo_patch_attach_step3_desc)
        )
        Text(
            text = stringResource(CarelevoStrings.carelevo_patch_attach_step4_desc),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CarelevoPatchAttachSection(
    stepLabel: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small), verticalAlignment = Alignment.Bottom) {
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Patch Attach")
@Composable
private fun CarelevoPatchFlowStep04AttachPreview() {
    MaterialTheme {
        CarelevoPatchFlowStep04AttachContent(
            onReadyClick = {}
        )
    }
}

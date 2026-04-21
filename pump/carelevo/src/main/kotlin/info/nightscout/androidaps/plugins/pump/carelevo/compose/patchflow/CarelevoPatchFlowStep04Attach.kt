package info.nightscout.androidaps.plugins.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.nightscout.androidaps.plugins.pump.carelevo.R
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.type.CarelevoPatchStep
import info.nightscout.androidaps.plugins.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            CarelevoPatchAttachSection(
                stepLabel = stringResource(R.string.carelevo_patch_step_1),
                title = stringResource(R.string.carelevo_patch_attach_step1_title),
                description = stringResource(R.string.carelevo_patch_attach_step1_desc)
            )
            CarelevoPatchAttachSection(
                stepLabel = stringResource(R.string.carelevo_patch_step_2),
                title = stringResource(R.string.carelevo_patch_attach_step2_title),
                description = stringResource(R.string.carelevo_patch_attach_step2_desc)
            )
            CarelevoPatchAttachSection(
                stepLabel = stringResource(R.string.carelevo_patch_step_3),
                title = stringResource(R.string.carelevo_patch_attach_step3_title),
                description = stringResource(R.string.carelevo_patch_attach_step3_desc)
            )
            Text(
                text = stringResource(R.string.carelevo_patch_attach_step4_desc),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            onClick = onReadyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            PatchFlowButtonText(text = stringResource(R.string.carelevo_btn_ready_complete))
        }
    }
}

@Composable
private fun CarelevoPatchAttachSection(
    stepLabel: String,
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
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

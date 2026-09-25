package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.compose.dialog.CarelevoInsulinRefillGuideDialog
import app.aaps.pump.carelevo.config.FillConfig
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.viewmodel.CarelevoPatchConnectionFlowViewModel

@Composable
internal fun CarelevoPatchFlowStep01Start(
    viewModel: CarelevoPatchConnectionFlowViewModel,
    onExitFlow: () -> Unit
) {
    CarelevoPatchStartContent(
        onNextClick = { viewModel.setPage(CarelevoPatchStep.SET_AMOUNT) },
        onCancelClick = onExitFlow
    )
}

@Composable
private fun CarelevoPatchStartContent(
    onNextClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    var guideVisible by remember { mutableStateOf(false) }
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(CoreUiR.string.next),
            onClick = onNextClick
        ),
        secondaryButton = WizardButton(
            text = stringResource(CoreUiR.string.cancel),
            onClick = onCancelClick
        )
    ) {
        Text(
            text = stringResource(R.string.carelevo_title_fill_insulin),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.carelevo_notice_fill_insulin_amount, FillConfig.FILL_MIN_UNITS, FillConfig.FILL_MAX_UNITS),
            style = MaterialTheme.typography.bodyMedium
        )

        Button(onClick = { guideVisible = true }) {
            Text(text = stringResource(R.string.carelevo_btn_insulin_guide))
        }
    }
    if (guideVisible) {
        CarelevoInsulinRefillGuideDialog(onDismissRequest = { guideVisible = false })
    }
}

@Preview(showBackground = true, name = "Patch Start")
@Composable
private fun CarelevoPatchFlowStep01StartPreview() {
    MaterialTheme {
        CarelevoPatchStartContent(
            onNextClick = {},
            onCancelClick = {}
        )
    }
}

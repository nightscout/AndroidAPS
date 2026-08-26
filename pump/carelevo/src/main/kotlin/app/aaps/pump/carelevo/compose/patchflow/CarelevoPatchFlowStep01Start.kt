package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.carelevo.R
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
    var guideExpanded by remember { mutableStateOf(false) }
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
        CarelevoInsulinRefillGuideSection(
            expanded = guideExpanded,
            onExpand = { guideExpanded = true }
        )
    }
}

@Composable
private fun CarelevoInsulinRefillGuideSection(
    expanded: Boolean,
    onExpand: () -> Unit
) {
    if (!expanded) {
        Button(onClick = onExpand) {
            Text(text = stringResource(R.string.carelevo_btn_insulin_guide))
        }
        return
    }

    val steps = listOf(
        stringResource(R.string.carelevo_insulin_refill_step1),
        stringResource(R.string.carelevo_insulin_refill_step2),
        stringResource(R.string.carelevo_insulin_refill_step3),
        stringResource(R.string.carelevo_insulin_refill_step4),
        stringResource(R.string.carelevo_insulin_refill_step5),
        stringResource(R.string.carelevo_insulin_refill_step6),
        stringResource(R.string.carelevo_insulin_refill_step7),
        stringResource(R.string.carelevo_insulin_refill_step8),
        stringResource(R.string.carelevo_insulin_refill_step9),
        stringResource(R.string.carelevo_insulin_refill_step10),
        stringResource(R.string.carelevo_insulin_refill_step11),
        stringResource(R.string.carelevo_insulin_refill_step12)
    )
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
        Text(
            text = stringResource(R.string.carelevo_btn_insulin_guide),
            style = MaterialTheme.typography.titleSmall
        )
        steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium
            )
        }
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

package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep

@Composable
internal fun patchStepTitle(step: CarelevoPatchStep): String =
    when (step) {
        CarelevoPatchStep.PROFILE_GATE     -> stringResource(CoreUiR.string.pump_wizard_profile_gate_title)
        CarelevoPatchStep.SELECT_INSULIN   -> stringResource(CoreUiR.string.select_insulin)
        CarelevoPatchStep.PATCH_START      -> stringResource(R.string.carelevo_connect_prepare_title)
        CarelevoPatchStep.SET_AMOUNT       -> stringResource(R.string.patch_prepare_dialog_title_insulin_amount)
        CarelevoPatchStep.PATCH_CONNECT    -> stringResource(R.string.carelevo_connect_patch_title)
        CarelevoPatchStep.SAFETY_CHECK     -> stringResource(R.string.carelevo_connect_safety_check_title)
        CarelevoPatchStep.SITE_LOCATION    -> stringResource(CoreUiR.string.site_rotation)
        CarelevoPatchStep.PATCH_ATTACH     -> stringResource(R.string.carelevo_connect_patch_attach_title)
        CarelevoPatchStep.NEEDLE_INSERTION -> stringResource(R.string.carelevo_connect_needle_check_title)
    }

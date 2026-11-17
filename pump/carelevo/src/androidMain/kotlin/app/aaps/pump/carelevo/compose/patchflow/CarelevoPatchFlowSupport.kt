package app.aaps.pump.carelevo.compose.patchflow

import androidx.compose.runtime.Composable
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.carelevo.CarelevoStrings
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep

@Composable
internal fun patchStepTitle(step: CarelevoPatchStep): String =
    when (step) {
        CarelevoPatchStep.PROFILE_GATE     -> stringResource(CoreUiStrings.pump_wizard_profile_gate_title)
        CarelevoPatchStep.SELECT_INSULIN   -> stringResource(CoreUiStrings.select_insulin)
        CarelevoPatchStep.PATCH_START      -> stringResource(CarelevoStrings.carelevo_connect_prepare_title)
        CarelevoPatchStep.SET_AMOUNT       -> stringResource(CarelevoStrings.patch_prepare_dialog_title_insulin_amount)
        CarelevoPatchStep.PATCH_CONNECT    -> stringResource(CarelevoStrings.carelevo_connect_patch_title)
        CarelevoPatchStep.SAFETY_CHECK     -> stringResource(CarelevoStrings.carelevo_connect_safety_check_title)
        CarelevoPatchStep.SITE_LOCATION    -> stringResource(CoreUiStrings.site_rotation)
        CarelevoPatchStep.PATCH_ATTACH     -> stringResource(CarelevoStrings.carelevo_connect_patch_attach_title)
        CarelevoPatchStep.NEEDLE_INSERTION -> stringResource(CarelevoStrings.carelevo_connect_needle_check_title)
    }

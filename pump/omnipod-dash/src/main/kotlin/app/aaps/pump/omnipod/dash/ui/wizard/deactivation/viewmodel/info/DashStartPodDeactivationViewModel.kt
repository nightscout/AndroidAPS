package app.aaps.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.info

import androidx.annotation.StringRes
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.StartPodDeactivationViewModel
import javax.inject.Inject

class DashStartPodDeactivationViewModel @Inject constructor() : StartPodDeactivationViewModel() {

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_title

    @StringRes
    override fun getTextId() = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_deactivation_wizard_start_pod_deactivation_text
}

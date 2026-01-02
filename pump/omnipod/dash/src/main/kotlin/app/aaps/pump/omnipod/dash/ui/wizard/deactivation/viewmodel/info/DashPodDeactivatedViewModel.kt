package app.aaps.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.info

import androidx.annotation.StringRes
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.info.PodDeactivatedViewModel
import javax.inject.Inject

class DashPodDeactivatedViewModel @Inject constructor() : PodDeactivatedViewModel() {

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_title

    @StringRes
    override fun getTextId() = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_deactivation_wizard_pod_deactivated_text
}
